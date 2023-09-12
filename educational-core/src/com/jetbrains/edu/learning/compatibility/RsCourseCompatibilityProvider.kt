package com.jetbrains.edu.learning.compatibility

import com.jetbrains.edu.EducationalCoreIcons
import com.jetbrains.edu.learning.courseFormat.PluginInfo
import com.jetbrains.edu.learning.courseFormat.PluginInfos
import javax.swing.Icon

class RsCourseCompatibilityProvider : CourseCompatibilityProvider {

  override fun requiredPlugins(): List<PluginInfo> {
    return listOf(
      PluginInfos.RUST,
      PluginInfos.TOML
    )
  }

  override val technologyName: String get() = "Rust"
  override val logo: Icon get() = EducationalCoreIcons.RustLogo
}
