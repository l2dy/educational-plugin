package com.jetbrains.edu.yaml.messages

import com.intellij.CommonBundle
import com.jetbrains.edu.learning.messages.EduBundle
import org.jetbrains.annotations.NonNls
import org.jetbrains.annotations.PropertyKey

object EduYAMLBundle : EduBundle() {

  @JvmStatic
  fun message(@PropertyKey(resourceBundle = BUNDLE) key: String, vararg params: Any): String {
    return CommonBundle.message(getBundle(BUNDLE), key, *params)
  }

  @NonNls
  private const val BUNDLE = "messages.EduYAMLBundle"
}