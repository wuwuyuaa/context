package com.threadmap.core.integration;

import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@Repository
class SampleRepository {
    String load() { return "row"; }
}

@Service
class SampleService {
    private final SampleRepository repository;
    SampleService(SampleRepository repository) { this.repository = repository; }
    String doWork() { return repository.load().toUpperCase(); }
}

@RestController
class SampleController {
    private final SampleService service;
    SampleController(SampleService service) { this.service = service; }

    @GetMapping("/sample")
    String handle() { return service.doWork(); }
}
