package com.jetbrains.edu.learning.newproject.coursesStorage

import CourseMetaInfo
import com.intellij.ide.RecentProjectsManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.*
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.util.messages.Topic
import com.intellij.util.xmlb.annotations.XCollection
import com.jetbrains.edu.learning.courseFormat.Course
import com.jetbrains.edu.learning.messages.EduCoreBundle
import com.jetbrains.edu.learning.newproject.ui.coursePanel.groups.CoursesGroup


@State(name = "CoursesStorage", storages = [Storage("coursesStorage.xml", roamingType = RoamingType.DISABLED)])
@Service
class CoursesStorage : SimplePersistentStateComponent<UserCoursesState>(UserCoursesState()) {

  fun addCourse(course: Course, location: String, tasksSolved: Int = 0, tasksTotal: Int = 0) {
    state.addCourse(course, location, tasksSolved, tasksTotal)
    ApplicationManager.getApplication().messageBus.syncPublisher(COURSE_ADDED).courseAdded(course)
  }

  fun getCoursePath(course: Course): String? = getCourseMetaInfo(course)?.location

  fun hasCourse(course: Course): Boolean = getCoursePath(course) != null

  fun getCourseMetaInfoForAnyLanguage(course: Course): CourseMetaInfo? {
    return state.courses.find {
      it.name == course.name
      && it.id == course.id
      && it.courseMode == course.courseMode
    }
  }

  fun getCourseMetaInfo(course: Course): CourseMetaInfo? {
    return state.courses.find {
      it.name == course.name
      && it.id == course.id
      && it.courseMode == course.courseMode
      && it.languageId == course.languageId
    }
  }

  fun updateCourseProgress(course: Course, location: String, tasksSolved: Int, tasksTotal: Int) {
    state.updateCourseProgress(course, location, tasksSolved, tasksTotal)
  }

  fun removeCourseByLocation(location: String) {
    val deletedCourse = state.removeCourseByLocation(location) ?: return
    ApplicationManager.getApplication().messageBus.syncPublisher(COURSE_DELETED).courseDeleted(deletedCourse)
    RecentProjectsManager.getInstance().removePath(location)
  }

  fun coursesInGroups(): List<CoursesGroup> {
    val courses = state.courses.toMutableList()
    val solvedCourses = CoursesGroup(EduCoreBundle.message("course.dialog.completed"),
      courses
        .filter { it.isStudy && it.tasksSolved != 0 && it.tasksSolved == it.tasksTotal }
        .map { it.toCourse() }
    )
    val courseCreatorCourses = CoursesGroup(EduCoreBundle.message("course.dialog.my.courses.course.creation"),
      courses.filter { !it.isStudy }.map { it.toCourse() })
    val inProgressCourses = CoursesGroup(EduCoreBundle.message("course.dialog.in.progress"),
      courses
        .filter { it.isStudy && (it.tasksSolved == 0 || it.tasksSolved != it.tasksTotal) }
        .map { it.toCourse() }
    )

    return listOf(courseCreatorCourses, inProgressCourses, solvedCourses).filter { it.courses.isNotEmpty() }
  }

  fun isNotEmpty() = state.courses.isNotEmpty()

  companion object {
    val COURSE_DELETED = Topic.create("Edu.courseDeletedFromStorage", CourseDeletedListener::class.java)
    val COURSE_ADDED = Topic.create("Edu.courseAddedToStorage", CourseAddedListener::class.java)

    fun getInstance(): CoursesStorage = service()
  }
}

class UserCoursesState : BaseState() {
  //  courses list is not updated on course removal and could contain removed courses.
  @get:XCollection(style = XCollection.Style.v2)
  val courses by list<CourseMetaInfo>()

  fun addCourse(course: Course, location: String, tasksSolved: Int = 0, tasksTotal: Int = 0) {
    val systemIndependentLocation = FileUtilRt.toSystemIndependentName(location)
    courses.removeIf { it.location == systemIndependentLocation }
    val courseMetaInfo = CourseMetaInfo(systemIndependentLocation, course, tasksTotal, tasksSolved)
    courses.add(courseMetaInfo)
  }

  fun removeCourseByLocation(location: String): CourseMetaInfo? {
    val courseMetaInfo = courses.find { it.location == location }
    courses.remove(courseMetaInfo)
    return courseMetaInfo
  }

  fun updateCourseProgress(course: Course, location: String, tasksSolved: Int, tasksTotal: Int) {
    val systemIndependentLocation = FileUtilRt.toSystemIndependentName(location)
    val courseMetaInfo = courses.find { it.location == systemIndependentLocation }
    if (courseMetaInfo != null) {
      courseMetaInfo.tasksSolved = tasksSolved
      courseMetaInfo.tasksTotal = tasksTotal
      intIncrementModificationCount()
    }
    else {
      courses.add(CourseMetaInfo(systemIndependentLocation, course, tasksTotal, tasksSolved))
    }
  }
}