
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.labels.ActionLink
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.tree.TreeUtil
import com.jetbrains.edu.coursecreator.actions.CCNewCourseAction
import com.jetbrains.edu.learning.messages.EduCoreBundle
import com.jetbrains.edu.learning.newproject.ui.CoursesDialogFontManager
import com.jetbrains.edu.learning.newproject.ui.CoursesPlatformProvider
import com.jetbrains.edu.learning.newproject.ui.CoursesPlatformProviderFactory
import com.jetbrains.edu.learning.newproject.ui.coursePanel.CoursePanel
import com.jetbrains.edu.learning.newproject.ui.myCourses.MyCoursesProvider
import com.jetbrains.edu.learning.taskDescription.ui.styleManagers.TypographyManager
import java.awt.BorderLayout
import java.awt.Component
import java.awt.FlowLayout
import java.awt.Font
import javax.swing.JPanel
import javax.swing.JTree
import javax.swing.ScrollPaneConstants
import javax.swing.event.TreeSelectionListener
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeCellRenderer

private const val PROVIDER_TOP_BOTTOM_OFFSET = 11
private const val PROVIDER_LEFT_OFFSET = 5
private const val ICON_TEXT_GAP = 8
private const val SCROLL_PANE_WIDTH = 233
private const val SCROLL_PANE_HEIGHT = 800
private const val FONT_SIZE = 13.0f

class CoursesProvidersSidePanel(private val myCoursesProvider: MyCoursesProvider) : JBScrollPane() {
  private val tree = createCourseProvidersTree()

  init {
    val panel = JPanel(BorderLayout())
    panel.add(tree, BorderLayout.CENTER)
    panel.add(createCourseActionLink(), BorderLayout.SOUTH)

    setViewportView(panel)
    horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
    verticalScrollBarPolicy = ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER
    preferredSize = JBUI.size(SCROLL_PANE_WIDTH, SCROLL_PANE_HEIGHT)
    border = JBUI.Borders.customLine(CoursePanel.DIVIDER_COLOR, 0, 0, 0, 1)
  }

  private fun createCourseProvidersTree(): Tree {
    val root = DefaultMutableTreeNode("")
    val allCoursesNode = DefaultMutableTreeNode(EduCoreBundle.message("course.dialog.all.courses"))
    CoursesPlatformProviderFactory.allProviders.forEach { allCoursesNode.add(DefaultMutableTreeNode(it)) }
    root.add(allCoursesNode)
    root.add(DefaultMutableTreeNode(myCoursesProvider))

    return Tree(root).apply {
      isRootVisible = false
      rowHeight = 0 // force row to calculate size basing on its content
      showsRootHandles = false
      border = null
      cellRenderer = ProviderWithIconCellRenderer()
      TreeUtil.expandAll(this)
      focusListeners.forEach { removeFocusListener(it) }
      treeExpansionListeners.forEach { removeTreeExpansionListener(it) }
      setSelectionRow(1)
    }
  }

  private fun createCourseActionLink(): ActionLink {
    return ActionLink(EduCoreBundle.message("course.dialog.create.course"), CCNewCourseAction()).apply {
      border = JBUI.Borders.empty(12, 12)
    }
  }

  fun addTreeSelectionListener(listener: TreeSelectionListener) {
    tree.addTreeSelectionListener(listener)
  }

  private class ProviderWithIconCellRenderer : DefaultTreeCellRenderer() {
    private val component = JPanel(FlowLayout(FlowLayout.LEFT, ICON_TEXT_GAP, 0))
    private val textLabel = JBLabel()
    private val iconLabel = JBLabel()

    init {
      textLabel.font = Font(TypographyManager().bodyFont, Font.PLAIN, CoursesDialogFontManager.fontSize)
      component.border = JBUI.Borders.empty(PROVIDER_TOP_BOTTOM_OFFSET, 0)
      component.add(iconLabel)
      component.add(textLabel)
    }

    override fun getTreeCellRendererComponent(tree: JTree?,
                                              value: Any?,
                                              selected: Boolean,
                                              expanded: Boolean,
                                              leaf: Boolean,
                                              row: Int,
                                              hasFocus: Boolean): Component {
      if (value is DefaultMutableTreeNode) {
        val userObject = value.userObject
        val tabName = if (userObject is CoursesPlatformProvider) userObject.name else userObject.toString()
        when (userObject) {
          is MyCoursesProvider, is String -> {
            val additionalText = (userObject as? MyCoursesProvider)?.additionalText ?: ""
            textLabel.text = UIUtil.toHtml("<b>$tabName</b>$additionalText")
            iconLabel.icon = null
            component.border = JBUI.Borders.empty(PROVIDER_TOP_BOTTOM_OFFSET, 0)
          }
          is CoursesPlatformProvider -> {
            textLabel.text = tabName
            iconLabel.icon = userObject.icon
            component.border = JBUI.Borders.empty(PROVIDER_TOP_BOTTOM_OFFSET, PROVIDER_LEFT_OFFSET, PROVIDER_TOP_BOTTOM_OFFSET, 0)
          }
        }
        textLabel.foreground = UIUtil.getListForeground(selected, hasFocus)
      }

      return component
    }
  }
}