package com.jetbrains.edu.cpp.messages

import com.jetbrains.edu.learning.messages.EduBundle
import org.jetbrains.annotations.NonNls
import org.jetbrains.annotations.PropertyKey

@NonNls
private const val BUNDLE = "messages.EduCppBundle"

object EduCppBundle: EduBundle(BUNDLE) {

  fun message(@PropertyKey(resourceBundle = BUNDLE) key: String, vararg params: Any): String {
    return getMessage(key, *params)
  }
}