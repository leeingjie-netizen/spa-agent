package com.lee.spa;

import com.lee.spa.dto.CreateAppointmentRequest;
import com.lee.spa.entity.Appointment;
import com.lee.spa.service.AppointmentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

@SpringBootTest
public class AppointmentConcurrencyTest {

    @Autowired
    private AppointmentService appointmentService;

    @Test
    void testConcurrentAppointment() throws Exception {

        CreateAppointmentRequest requestA = new CreateAppointmentRequest();

        requestA.setUserId(1L);

        requestA.setServiceId(2L);

        requestA.setStaffId(2L);

        requestA.setAppointmentDate(LocalDate.of(2026,9,23));

        requestA.setStartTime(LocalTime.of(16,30));

        requestA.setRemark("并发测试A");

        requestA.setRequestId("idem-concurrent-001");


        CreateAppointmentRequest requestB = new CreateAppointmentRequest();

        requestB.setUserId(2L);
        requestB.setServiceId(3L);
        requestB.setStaffId(2L);
        requestB.setAppointmentDate(
                LocalDate.of(2026, 9, 23)
        );
        requestB.setStartTime(
                LocalTime.of(16, 30)
        );
        requestB.setRemark("并发测试-B");
        requestB.setRequestId("idem-concurrent-001");

//        创建一个最多同时运行两个线程的线程池。
        ExecutorService executor = Executors.newFixedThreadPool(2);

//        这个闸门还剩 1 次 countDown() 才打开。
        CountDownLatch startLatch = new CountDownLatch(1);

        Future<?> futureA = executor.submit(() -> {

            try {

                startLatch.await();

                Appointment appointment =
                        appointmentService.createAppointment(requestA);

                System.out.println(
                        "A 预约成功，appointmentId = "
                                + appointment.getId()
                );

            } catch (Exception e) {

                System.out.println(
                        "A 预约失败：" + e.getMessage()
                );
            }

        });

        Future<?> futureB = executor.submit(() -> {

            try {

                startLatch.await();

                Appointment appointment =
                        appointmentService
                                .createAppointment(requestB);

                System.out.println(
                        "B 预约成功，appointmentId = "
                                + appointment.getId()
                );

            } catch (Exception e) {

                System.out.println(
                        "B 预约失败：" + e.getMessage()
                );
            }

        });


        startLatch.countDown();

        futureA.get();
        futureB.get();

        executor.shutdown();
    }



}
