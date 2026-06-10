package com.courseqa.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.courseqa.model.dto.CourseDto;
import com.courseqa.model.entity.Chapter;
import com.courseqa.model.entity.Course;
import com.courseqa.model.entity.CourseWorkspace;
import com.courseqa.repository.ChapterRepository;
import com.courseqa.repository.CourseRepository;
import com.courseqa.repository.CourseWorkspaceRepository;

@Service
public class CourseService {
    // Course, chapter, and course workspace business logic.
    // TODO: Add business logic here.

@Autowired
private CourseRepository courseRepository;
@Autowired
private ChapterRepository chapterRepository;
@Autowired
private CourseWorkspaceRepository courseWorkspaceRepository;


//------------------Course methods------------------
public List<Course> getAllCourses(){
    return courseRepository.findByIsActiveTrue();
}

public Course createCourse(CourseDto.CreateCourseRequest request){
    if(courseRepository.existsByCourseCode(request.courseCode)){
        throw new RuntimeException("Course code already exists");
    }
     Course course = new Course();
        course.setCourseCode(request.courseCode);
        course.setCourseName(request.courseName);
        course.setDescription(request.description);
        course.setCreatedBy(request.createdBy);
        course.setIsActive(true);
        course.setCreatedAt(LocalDateTime.now());
        course.setUpdatedAt(LocalDateTime.now());

        return courseRepository.save(course);
    }


//------------------Chapter methods------------------
    public List<Chapter> getChapterByCourse(UUID courseId){
        return chapterRepository.findByCourseIdOrderByOrderIndexAsc(courseId);
    }


public Chapter createChapter(UUID courseId, CourseDto.CreateChapterRequest request){
    if(chapterRepository.existsByCourseIdAndChapterTitle(courseId, request.chapterTitle)){
        throw new RuntimeException("Chapter title already exists in this course");
    }

    Chapter chapter = new Chapter();
    chapter.setCourseId(courseId);
    chapter.setChapterTitle(request.chapterTitle);
    chapter.setDescription(request.description);
    chapter.setOrderIndex(request.orderIndex);
    chapter.setIsActive(true);
    chapter.setCreatedAt(LocalDateTime.now());
    chapter.setUpdatedAt(LocalDateTime.now());

    return chapterRepository.save(chapter);
}

//------------------Course workspace methods------------------
public List<CourseWorkspace> getCourseWorkspacesByCourse(UUID courseId){
    return courseWorkspaceRepository.findByCourseId(courseId);
}

public CourseWorkspace createWorkspace(UUID courseId, CourseDto.CreateWorkspaceRequest request){
    if(courseWorkspaceRepository.existsByCourseIdAndWorkspaceTitle(courseId, request.workspaceTitle)){
        throw new RuntimeException("Workspace title already exists in this course");
    }
   CourseWorkspace workspace = new CourseWorkspace();
   workspace.setCourseId(courseId);
   workspace.setOwnerUserId(courseId);
   workspace.setWorkspaceTitle(request.workspaceTitle);
   workspace.setDescription(request.description);
   workspace.setIsActive(true);
   workspace.setCreatedAt(LocalDateTime.now());
   workspace.setUpdatedAt(LocalDateTime.now());
   
    return courseWorkspaceRepository.save(workspace);
}
 
}
    