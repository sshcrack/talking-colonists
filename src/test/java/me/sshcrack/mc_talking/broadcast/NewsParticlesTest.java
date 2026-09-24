package me.sshcrack.mc_talking.broadcast;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NewsParticlesTest {
    @Test
    void arcStartsAndEndsAtTheHeadsAndRisesInBetween() {
        Vec3 start = new Vec3(0, 65, 0);
        Vec3 end = new Vec3(4, 65, 0);
        assertEquals(start, NewsParticles.pointOnArc(start, end, 0));
        Vec3 last = NewsParticles.pointOnArc(start, end, 1);
        assertEquals(4, last.x, 1e-9);
        assertEquals(65, last.y, 1e-9);
        Vec3 middle = NewsParticles.pointOnArc(start, end, 0.5);
        assertEquals(2, middle.x, 1e-9);
        assertEquals(65.8, middle.y, 1e-9);
    }
}
