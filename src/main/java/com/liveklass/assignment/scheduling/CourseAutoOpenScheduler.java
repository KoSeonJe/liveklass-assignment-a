package com.liveklass.assignment.scheduling;

import com.liveklass.assignment.facade.CourseFacade;
import com.liveklass.assignment.service.CourseStatusChangeResult;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CourseAutoOpenScheduler {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final Logger log = LoggerFactory.getLogger(CourseAutoOpenScheduler.class);

    private final CourseFacade courseFacade;
    private final Clock clock;

    @Scheduled(cron = "0 0 0 * * *", zone = "Asia/Seoul")
    public void run() {
        LocalDate today = LocalDate.now(clock.withZone(KST));
        try {
            List<CourseStatusChangeResult> results = courseFacade.autoOpenDueDrafts(today);
            log.info("Auto-opened {} DRAFT courses on {}", results.size(), today);
        } catch (Exception e) {
            log.error("Auto-open scheduler failed on {}", today, e);
        }
    }
}
