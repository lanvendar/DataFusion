package com.datafusion.manager.scheduler.model;

import com.datafusion.common.exception.CommonException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 业务来源定位信息测试.
 *
 * @author datafusion
 * @version 1.0.0, 2026/7/13
 * @since 1.0.0
 */
class BusinessSourceRouteTest {

    @Test
    void shouldEncodeAndDecodeSourceRoute() {
        BusinessSourceRoute route = route("SQL 开发", "job:1=2", "2026-07-13 15:30:00", "/job?id=1:2");

        String sourceRoute = route.toSourceRoute();
        BusinessSourceRoute parsed = BusinessSourceRoute.parse(sourceRoute);

        assertEquals("bizref:v1:bizSystem=SQL%20%E5%BC%80%E5%8F%91:bizKey=job%3A1%3D2:"
                + "bizVersion=2026-07-13%2015%3A30%3A00:bizUrl=%2Fjob%3Fid%3D1%3A2", sourceRoute);
        assertEquals(route, parsed);
    }

    @Test
    void shouldKeepIdentityStableAcrossVersionChanges() {
        BusinessSourceRoute first = route("SPIDER", "sci99", "1.0.0", null);
        BusinessSourceRoute second = route("SPIDER", "sci99", "2.0.0", "/spider/sci99");

        assertEquals(first.identity(), second.identity());
    }

    @Test
    void shouldRejectMalformedSourceRoute() {
        assertThrows(CommonException.class,
                () -> BusinessSourceRoute.parse("bizref:v1:bizKey=job-1:bizSystem=SYSTEM:bizVersion=1.0.0"));
        assertThrows(CommonException.class,
                () -> BusinessSourceRoute.parse("bizref:v2:bizSystem=SYSTEM:bizKey=job-1:bizVersion=1.0.0"));
        assertNull(BusinessSourceRoute.parse(null));
    }

    private BusinessSourceRoute route(String bizSystem, String bizKey, String bizVersion, String bizUrl) {
        BusinessSourceRoute route = new BusinessSourceRoute();
        route.setBizSystem(bizSystem);
        route.setBizKey(bizKey);
        route.setBizVersion(bizVersion);
        route.setBizUrl(bizUrl);
        return route;
    }
}
