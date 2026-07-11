package com.kbpack.task;

import com.kbpack.parser.ParsePipeline;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class ParseTaskWorker {
    private final ParseTaskRepository taskRepository;
    private final ParsePipeline pipeline;
    private final ParseTaskStateService stateService;

    public ParseTaskWorker(ParseTaskRepository taskRepository, ParsePipeline pipeline, ParseTaskStateService stateService) {
        this.taskRepository = taskRepository;
        this.pipeline = pipeline;
        this.stateService = stateService;
    }

    public void process(UUID taskId) {
        try {
            ParseTask task = taskRepository.findById(taskId).orElse(null);
            if (task == null || task.getStatus() != ParseTask.Status.processing) return;
            pipeline.process(task.getVersionId());
            stateService.complete(taskId);
        } catch (Throwable error) {
            stateService.fail(taskId, error);
        }
    }
}
