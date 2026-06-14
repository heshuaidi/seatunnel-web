package org.apache.seatunnel.web.api.service.impl;

import jakarta.annotation.PostConstruct;
import org.apache.seatunnel.web.api.service.HoconRenderService;
import org.apache.seatunnel.web.core.exceptions.ServiceException;
import org.apache.seatunnel.web.dao.entity.JobDefinitionEntity;
import org.apache.seatunnel.web.dao.entity.SyncIncrementalConfigEntity;
import org.apache.seatunnel.web.spi.bean.dto.BatchLinkUpIncrementalConfigRequest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
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

    @Test
    void contextJsonShouldAcceptEmptyAndObject() {
        BatchLinkUpIncrementalServiceImpl service =
                new BatchLinkUpIncrementalServiceImpl(Mockito.mock(HoconRenderService.class));
        BatchLinkUpIncrementalConfigRequest request = new BatchLinkUpIncrementalConfigRequest();
        request.setDefaultParamsJson("");
        request.setCustomContextJson("{\"sink_table\":\"lab_sink_order\"}");

        SyncIncrementalConfigEntity entity = new SyncIncrementalConfigEntity();
        invokeFillConfig(service, entity, request);

        Assertions.assertEquals("{\"sink_table\":\"lab_sink_order\"}", entity.getCustomContextJson());
    }

    @Test
    void contextJsonShouldRejectArrayAndReservedKey() {
        BatchLinkUpIncrementalServiceImpl service =
                new BatchLinkUpIncrementalServiceImpl(Mockito.mock(HoconRenderService.class));

        BatchLinkUpIncrementalConfigRequest arrayRequest = new BatchLinkUpIncrementalConfigRequest();
        arrayRequest.setDefaultParamsJson("[]");
        ServiceException arrayError = Assertions.assertThrows(
                ServiceException.class,
                () -> invokeFillConfig(service, new SyncIncrementalConfigEntity(), arrayRequest)
        );
        Assertions.assertTrue(arrayError.getMessage().contains("default_params_json must be JSON object"));

        BatchLinkUpIncrementalConfigRequest reservedRequest = new BatchLinkUpIncrementalConfigRequest();
        reservedRequest.setCustomContextJson("{\"batch_id\":\"xxx\"}");
        ServiceException reservedError = Assertions.assertThrows(
                ServiceException.class,
                () -> invokeFillConfig(service, new SyncIncrementalConfigEntity(), reservedRequest)
        );
        Assertions.assertTrue(reservedError.getMessage().contains("custom_context_json contains reserved key: batch_id"));
    }

    private static void invokeFillConfig(
            BatchLinkUpIncrementalServiceImpl service,
            SyncIncrementalConfigEntity entity,
            BatchLinkUpIncrementalConfigRequest request
    ) {
        try {
            Method method = BatchLinkUpIncrementalServiceImpl.class.getDeclaredMethod(
                    "fillConfig",
                    JobDefinitionEntity.class,
                    SyncIncrementalConfigEntity.class,
                    BatchLinkUpIncrementalConfigRequest.class
            );
            method.setAccessible(true);
            JobDefinitionEntity definition = new JobDefinitionEntity();
            definition.setId(1L);
            method.invoke(service, definition, entity, request);
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof RuntimeException) {
                throw (RuntimeException) e.getCause();
            }
            throw new RuntimeException(e.getCause());
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }
}
