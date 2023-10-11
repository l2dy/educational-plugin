package com.jetbrains.edu.learning.command

import com.intellij.execution.testframework.sm.ServiceMessageBuilder
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.jetbrains.edu.learning.actions.CheckAction
import com.jetbrains.edu.learning.checker.CheckListener
import com.jetbrains.edu.learning.courseFormat.*
import com.jetbrains.edu.learning.courseFormat.ext.getPathInCourse
import com.jetbrains.edu.learning.courseFormat.ext.getVirtualFile
import com.jetbrains.edu.learning.courseFormat.tasks.Task
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/**
 * Adds `validateCourse` command for IDE to validate given course and report potential errors
 *
 * Expected usages:
 * - `validateCourse %/path/to/project/dir% --marketplace %marketplace-course-link%`
 * - `validateCourse %/path/to/project/dir% --archive %/path/to/course/archive%`
 *
 * Currently, the command is supposed to be run on TeamCity,
 * so test messages use TeamCity [test message format](https://www.jetbrains.com/help/teamcity/service-messages.html#Reporting+Tests).
 */
class EduCourseValidatorAppStarter : EduCourseProjectAppStarterBase() {
  @Suppress("OVERRIDE_DEPRECATION")
  override val commandName: String
    get() = "validateCourse"

  override val courseMode: CourseMode
    get() = CourseMode.EDUCATOR

  override suspend fun performProjectAction(project: Project, course: Course): CommandResult {
    val result = withValidationEnabled(project) {
      course.check(project)
    }

    return if (result) CommandResult.Ok else CommandResult.Error("Some tasks haven't finished successfully")
  }

  /**
   * Returns `true` if all tasks in container are checked successfully, `false` otherwise
   */
  private suspend fun ItemContainer.check(project: Project): Boolean {
    var result = true
    testSuite(name) {
      for (item in items) {
        val itemResult = when (item) {
          is ItemContainer -> {
            item.check(project)
          }
          is Task -> {
            item.check(project)
          }
          else -> error("Unreachable")
        }
        result = result && itemResult
      }
    }
    return result
  }

  private suspend fun <T> testSuite(name: String, action: suspend () -> T): T {
    println(ServiceMessageBuilder.testSuiteStarted(name))
    return try {
      action()
    }
    finally {
      println(ServiceMessageBuilder.testSuiteFinished(name))
    }
  }

  /**
   * Returns `true` if a task is checked successfully, `false` otherwise
   */
  private suspend fun Task.check(project: Project): Boolean {
    println(ServiceMessageBuilder.testStarted(presentableName))
    withContext(Dispatchers.EDT) {
      prepareForChecking(project)
      val dataContext = SimpleDataContext.getProjectContext(project)
      ActionUtil.invokeAction(CheckAction(), dataContext, "", null, null)
    }

    val result = ValidationCheckResultManager.getInstance(project).getResult(this)
    val testMessage = when (result.status) {
      CheckStatus.Unchecked -> {
        ServiceMessageBuilder.testIgnored(presentableName)
          .addAttribute("message", result.message)
      }
      CheckStatus.Solved -> null
      CheckStatus.Failed -> {
        ServiceMessageBuilder.testFailed(presentableName)
          .addAttribute("message", result.message)
          .addAttribute("details", result.details.orEmpty())
      }
    }
    if (testMessage != null) {
      println(testMessage)
    }
    println(ServiceMessageBuilder.testFinished(presentableName))
    return result.isSolved
  }

  @RequiresEdt
  private fun Task.prepareForChecking(project: Project) {
    // TODO: properly open tasks inside framework lessons
    val taskFile = taskFiles.values.first()
    val virtualFile = taskFile.getVirtualFile(project)
                      ?: error("Can't find virtual file for `${taskFile.name}` task file in `$name` task")

    FileEditorManager.getInstance(project).openFile(virtualFile, true)
  }
}

@Service(Service.Level.PROJECT)
private class ValidationCheckResultManager {

  private val results: MutableMap<Task, CheckResult> = ConcurrentHashMap()

  @Volatile
  var validationEnabled: Boolean = false

  fun putCheckResult(task: Task, result: CheckResult) {
    require(task !in results) {
      "Task `${task.getPathInCourse()}` is already checked"
    }

    results[task] = result
  }

  suspend fun getResult(task: Task): CheckResult {
    require(validationEnabled) {
      "`ValidationCheckResultManager.validationEnabled` should be true. Otherwise, check results are not collected"
    }

    while (task !in results) {
      delay(100)
    }
    return results.getValue(task)
  }

  companion object {
    fun getInstance(project: Project): ValidationCheckResultManager = project.service()
  }
}

private suspend fun <T> withValidationEnabled(project: Project, action: suspend () -> T): T {
  val validationManager = ValidationCheckResultManager.getInstance(project)
  validationManager.validationEnabled = true
  return try {
    action()
  }
  finally {
    validationManager.validationEnabled = false
  }
}

class ValidationCheckListener : CheckListener {

  override fun afterCheck(project: Project, task: Task, result: CheckResult) {
    val validationManager = ValidationCheckResultManager.getInstance(project)
    if (validationManager.validationEnabled) {
      validationManager.putCheckResult(task, result)
    }
  }
}
