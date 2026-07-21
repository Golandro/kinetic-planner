package net.jsmua.kinetic_planner.data;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class StubRailwayDataAccessTest {
    @Test
    void clientVersionIncrementsOnAddGraph() {
        StubRailwayDataAccess stub = new StubRailwayDataAccess();
        assertEquals(0, stub.clientVersion());
        stub.addGraph(null);
        assertEquals(1, stub.clientVersion());
    }

    // edgeGeometry 测试暂时跳过：EdgeGeometry record 引用 TrackMaterial（Create），
    // 纯 JVM 测试环境 classpath 可能缺失 Create 运行时依赖
}
