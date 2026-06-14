package org.apache.seatunnel.web.api.service.impl;

import jakarta.annotation.PostConstruct;
import org.apache.seatunnel.web.api.service.HoconRenderService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

class BatchLinkUpIncrementalServiceImplInjectionTest {

    @Test
    void hoconRenderServiceShouldUseConstructorInjection() throws Exception {
        Field field = BatchLinkUpIncrementalServiceImpl.class.getDeclaredField("hoconRenderService");
        Constructor<BatchLinkUpIncrementalServiceImpl> constructor =
                BatchLinkUpIncrementalServiceImpl.class.getDeclaredConstructor(HoconRenderService.class);

        Assertions.assertTrue(Modifier.isFinal(field.getModifiers()));
        Assertions.assertEquals(1, constructor.getParameterCount());
    }

    @Test
    void validateInjectedDependenciesShouldRunAtStartup() throws Exception {
        Method method = BatchLinkUpIncrementalServiceImpl.class.getDeclaredMethod("validateInjectedDependencies");

        Assertions.assertTrue(method.isAnnotationPresent(PostConstruct.class));
    }
}
