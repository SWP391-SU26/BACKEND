package com.courseqa.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.courseqa.model.dto.ApiResponse;
import com.courseqa.model.dto.CourseDto;
import com.courseqa.model.entity.Chapter;
import com.courseqa.model.entity.Course;
import com.courseqa.model.entity.CourseWorkspace;
import com.courseqa.service.CourseService;



@RestController
@RequestMapping("/api/courses")
@CrossOrigin
public class CourseController {
    // Course, chapter, workspace APIs.
    // TODO: Add API endpoints here.
    @Autowired
    private CourseService courseService;
    @GetMapping
    public ResponseEntity<ApiResponse<List<Course>>> getAllCourses(){
        try{
            List<Course> courses = courseService.getAllCourses();
            return ResponseEntity.ok(ApiResponse.ok(courses));
        }catch(RuntimeException e){
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }
    
    @PostMapping
    public ResponseEntity<ApiResponse<Course>> createCourse(
        @RequestBody CourseDto.CreateCourseRequest request){
            try{
                Course course = courseService.createCourse(request);
                return ResponseEntity.ok(ApiResponse.ok(course));
            }catch(RuntimeException e){
                return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
            }
        }
    
@GetMapping("/{courseId}/chapters")
public ResponseEntity<ApiResponse<List<Chapter>>> getChapters(
         @PathVariable UUID courseId){
try{
    List<Chapter> chapters = courseService.getChapterByCourse(courseId);
    return ResponseEntity.ok(ApiResponse.ok(chapters));
}catch(RuntimeException e){
    return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
}

 }

@PostMapping("/{courseId}/chapters")
public ResponseEntity<ApiResponse<Chapter>> createChapter(
    @PathVariable UUID courseId,
    @RequestBody CourseDto.CreateChapterRequest request){
    try{
        Chapter chapter = courseService.createChapter(courseId, request);
        return ResponseEntity.ok(ApiResponse.ok(chapter));
    }catch(RuntimeException e){
        return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
    }
    }

@GetMapping("/{courseId}/workspaces")
    public ResponseEntity<ApiResponse<List<CourseWorkspace>>> getWorkspaces(
            @PathVariable UUID courseId) {
        try {
            List<CourseWorkspace> workspaces = courseService.getWorkspacesByCourse(courseId);
            return ResponseEntity.ok(ApiResponse.ok(workspaces));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }
   
   
@PostMapping("{courseId}/workspaces")
public ResponseEntity<ApiResponse<CourseWorkspace>> createWorkspace(
    @PathVariable UUID courseId,
    @RequestBody CourseDto.CreateWorkspaceRequest request){

try{
    CourseWorkspace workspace = courseService.createWorkspace(courseId, request);
    return ResponseEntity.ok(ApiResponse.ok(workspace));    
}catch(RuntimeException e){
    return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
    }

}
}
