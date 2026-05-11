package com.meet.miniclaude.service;

import com.meet.miniclaude.domain.Student;
import com.meet.miniclaude.dto.CreateStudentRequest;
import com.meet.miniclaude.dto.StudentResponse;
import com.meet.miniclaude.exception.ResourceNotFoundException;
import com.meet.miniclaude.repository.StudentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StudentService {

    private final StudentRepository studentRepository;

    public List<StudentResponse> getAllStudents() {
        return studentRepository.findAll().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public StudentResponse getStudentById(Long id) {
        Student student = studentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Student", id));
        return toResponse(student);
    }

    @Transactional
    public StudentResponse createStudent(CreateStudentRequest request) {
        Student student = Student.builder()
                .name(request.name())
                .email(request.email())
                .build();
        Student saved = studentRepository.save(student);
        return toResponse(saved);
    }

    @Transactional
    public StudentResponse updateStudent(Long id, CreateStudentRequest request) {
        Student student = studentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Student", id));
        student.setName(request.name());
        student.setEmail(request.email());
        Student updated = studentRepository.save(student);
        return toResponse(updated);
    }

    @Transactional
    public void deleteStudent(Long id) {
        if (!studentRepository.existsById(id)) {
            throw new ResourceNotFoundException("Student", id);
        }
        studentRepository.deleteById(id);
    }

    private StudentResponse toResponse(Student student) {
        return new StudentResponse(
                student.getId(),
                student.getName(),
                student.getEmail()
        );
    }
}