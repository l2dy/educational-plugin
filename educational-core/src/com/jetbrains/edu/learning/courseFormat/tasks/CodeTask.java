package com.jetbrains.edu.learning.courseFormat.tasks;

import com.jetbrains.edu.learning.courseFormat.CheckStatus;
import org.jetbrains.annotations.NotNull;

import java.util.Date;

public class CodeTask extends Task {
  public static final String CODE_TASK_TYPE = "code";

  @SuppressWarnings("unused") //used for deserialization
  public CodeTask() {}

  public CodeTask(@NotNull final String name) {
    super(name);
  }

  public CodeTask(@NotNull final String name, int id, int position, @NotNull Date updateDate, @NotNull CheckStatus status) {
    super(name, id, position, updateDate, status);
  }

  @Override
  public String getItemType() {
    return CODE_TASK_TYPE;
  }

  @Override
  public boolean supportSubmissions() {
    return true;
  }

  @Override
  public boolean isPluginTaskType() {
    return false;
  }
}
