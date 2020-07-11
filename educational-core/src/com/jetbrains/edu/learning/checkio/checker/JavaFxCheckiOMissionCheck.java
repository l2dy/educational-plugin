package com.jetbrains.edu.learning.checkio.checker;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.jetbrains.edu.learning.checker.CheckResult;
import com.jetbrains.edu.learning.checkio.api.exceptions.NetworkException;
import com.jetbrains.edu.learning.checkio.connectors.CheckiOOAuthConnector;
import com.jetbrains.edu.learning.checkio.notifications.errors.handlers.CheckiOErrorHandler;
import com.jetbrains.edu.learning.checkio.utils.CheckiONames;
import com.jetbrains.edu.learning.courseFormat.CheckStatus;
import com.jetbrains.edu.learning.courseFormat.tasks.Task;
import com.jetbrains.edu.learning.taskDescription.ui.BrowserWindow;
import javafx.application.Platform;
import javafx.concurrent.Worker;
import netscape.javascript.JSObject;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.w3c.dom.Document;
import org.w3c.dom.html.HTMLFormElement;
import org.w3c.dom.html.HTMLInputElement;
import org.w3c.dom.html.HTMLTextAreaElement;

import javax.swing.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class JavaFxCheckiOMissionCheck extends CheckiOMissionCheck {
  private final BrowserWindow myBrowserWindow;
  private final CheckiOTestResultHandler myResultHandler;

  @Nullable private CheckResult myCheckResult;
  private final CountDownLatch myLatch = new CountDownLatch(1);
  private final CheckiOOAuthConnector myOAuthConnector;
  private final String myInterpreterName;
  private final String myTestFormTargetUrl;

  protected JavaFxCheckiOMissionCheck(
    @NotNull Task task,
    @NotNull Project project,
    @NotNull CheckiOOAuthConnector oAuthConnector,
    @NotNull String interpreterName,
    @NotNull String testFormTargetUrl
  ) {
    super(project, task);
    myOAuthConnector = oAuthConnector;
    myInterpreterName = interpreterName;
    myTestFormTargetUrl = testFormTargetUrl;

    myResultHandler = new CheckiOTestResultHandler();
    myBrowserWindow = new BrowserWindow(project, false);
  }

  @NotNull
  @Override
  public CheckResult call() {
    try {
      final String accessToken = myOAuthConnector.getAccessToken();
      final String taskId = String.valueOf(getTask().getId());
      final String code = getCodeFromTask();

      return doCheck(accessToken, taskId, code);
    } catch (InterruptedException e) {
      return new CheckResult(CheckStatus.Unchecked, "Checking was cancelled");
    } catch (Exception e) {
      new CheckiOErrorHandler(
        "Failed to check the task",
        myOAuthConnector
      ).handle(e);
      return CheckResult.getFailedToCheck();
    }
  }

  @NotNull
  private CheckResult doCheck(@NotNull String accessToken, @NotNull String taskId, @NotNull String code)
    throws InterruptedException, NetworkException {

    Platform.runLater(() -> {
      setTestFormLoadedListener(accessToken, taskId, code);
      setCheckDoneListener();
      loadTestForm();
    });

    boolean timeoutExceeded = !myLatch.await(30L, TimeUnit.SECONDS);
    if (timeoutExceeded) {
      return new CheckResult(CheckStatus.Unchecked, "Checking took too much time");
    }

    if (myCheckResult == CheckResult.CONNECTION_FAILED) {
      throw new NetworkException();
    }

    //noinspection ConstantConditions cannot be null because of handler implementation
    return myCheckResult;
  }

  private void loadTestForm() {
    final String formUrl = getClass().getResource(CheckiONames.CHECKIO_TEST_FORM_URL).toExternalForm();
    myBrowserWindow.getEngine().load(formUrl);
  }

  private void setCheckDoneListener() {
    final Ref<Boolean> visited = new Ref<>(Boolean.FALSE);

    myBrowserWindow.getEngine().getLoadWorker().stateProperty().addListener((observable, oldState, newState) -> {
      if (newState == Worker.State.FAILED) {
        setConnectionError();
        return;
      }

      if (myBrowserWindow.getEngine().getLocation().contains(CheckiONames.CHECKIO_URL) && newState == Worker.State.SUCCEEDED && !visited.get()) {
        visited.set(Boolean.TRUE);

        final JSObject windowObject = (JSObject)myBrowserWindow.getEngine().executeScript("window");
        windowObject.setMember("javaHandler", myResultHandler);

        myBrowserWindow.getEngine().executeScript(
          "function handleEvent(e) {\n" +
          "\twindow.javaHandler.handleTestEvent(e.detail.success)\n" +
          "}\n" +
          "window.addEventListener(\"checkio:checkDone\", handleEvent, false)"
        );
      }
    });
  }

  private void setTestFormLoadedListener(@NotNull String accessToken,
                                         @NotNull String taskId,
                                         @NotNull String code) {
    myBrowserWindow.getEngine().getLoadWorker().stateProperty().addListener(((observable, oldState, newState) -> {
      if (newState == Worker.State.FAILED) {
        setConnectionError();
        return;
      }

      if (newState != Worker.State.SUCCEEDED) {
        return;
      }

      String location = myBrowserWindow.getEngine().getLocation();
      final Document document = myBrowserWindow.getEngine().getDocument();
      if (location.contains("checkioTestForm.html")) {
        ((HTMLInputElement)document.getElementById("access-token")).setValue(accessToken);
        ((HTMLInputElement)document.getElementById("task-id")).setValue(taskId);
        ((HTMLInputElement)document.getElementById("interpreter")).setValue(myInterpreterName);
        ((HTMLTextAreaElement)document.getElementById("code")).setValue(code);

        final HTMLFormElement testForm = (HTMLFormElement) document.getElementById("test-form");
        testForm.setAction(myTestFormTargetUrl);
        testForm.submit();
      }

      if (location.contains("check-html-output")) {
        applyCheckiOBackgroundColor(document);
      }
    }));
  }

  private static void applyCheckiOBackgroundColor(@NotNull final Document document) {
    document.getDocumentElement().setAttribute("style", "background-color : #DEE7F6;");
  }

  private void setConnectionError() {
    myCheckResult = CheckResult.CONNECTION_FAILED;
    myLatch.countDown();
  }

  @NotNull
  @Override
  public JComponent getPanel() {
    return myBrowserWindow.getPanel();
  }

  public class CheckiOTestResultHandler {
    @SuppressWarnings("unused") // used in JS code
    public void handleTestEvent(int result) {
      myCheckResult = result == 1 ?
                      new CheckResult(CheckStatus.Solved, "All tests passed") :
                      new CheckResult(CheckStatus.Failed, "Tests failed");
      myLatch.countDown();
    }
  }
}
