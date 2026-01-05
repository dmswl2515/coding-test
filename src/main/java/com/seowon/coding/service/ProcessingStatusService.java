package com.seowon.coding.service;

import com.seowon.coding.domain.model.ProcessingStatus;
import com.seowon.coding.domain.repository.ProcessingStatusRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProcessingStatusService {

    private final ProcessingStatusRepository processingStatusRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateProgress(String jobId, int processed, int total) {
        ProcessingStatus ps = processingStatusRepository.findByJobId(jobId)
                .orElseGet(() -> ProcessingStatus.builder().jobId(jobId).build());
        ps.updateProgress(processed, total);
        processingStatusRepository.save(ps);
    }
}
