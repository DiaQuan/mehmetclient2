package com.mehmetclient.client;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MappableRingBuffer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.system.MemoryUtil;

import java.util.List;
import java.util.OptionalDouble;
import java.util.OptionalInt;

/** Draws ESP wireframes using a no-depth-test pipeline so walls do not hide them. */
public final class ThroughWallEspRenderer {
    private ThroughWallEspRenderer() {}

    private static final RenderPipeline PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.LINES_SNIPPET)
                    .withLocation(Identifier.fromNamespaceAndPath("mehmetclient", "pipeline/esp_lines_through_walls"))
                    .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                    .build());
    private static final ByteBufferBuilder ALLOCATOR = new ByteBufferBuilder(RenderType.SMALL_BUFFER_SIZE);
    private static final Vector4f WHITE = new Vector4f(1f, 1f, 1f, 1f);
    private static final Vector3f ZERO = new Vector3f();
    private static final Matrix4f IDENTITY = new Matrix4f();
    private static MappableRingBuffer vertexBuffer;

    public static void draw(Minecraft client, PoseStack pose, List<Box> boxes) {
        if (boxes.isEmpty()) return;
        BufferBuilder builder = new BufferBuilder(ALLOCATOR, PIPELINE.getVertexFormatMode(), PIPELINE.getVertexFormat());
        for (Box box : boxes) addBox(builder, pose.last().pose(), box);
        MeshData mesh = builder.buildOrThrow();
        try {
            MeshData.DrawState state = mesh.drawState();
            VertexFormat format = state.format();
            int byteSize = state.vertexCount() * format.getVertexSize();
            if (vertexBuffer == null || vertexBuffer.size() < byteSize) {
                if (vertexBuffer != null) vertexBuffer.close();
                vertexBuffer = new MappableRingBuffer(() -> "Mehmet Client ESP", GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_MAP_WRITE, byteSize);
            }
            CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
            try (GpuBuffer.MappedView mapped = encoder.mapBuffer(
                    vertexBuffer.currentBuffer().slice(0, mesh.vertexBuffer().remaining()), false, true)) {
                MemoryUtil.memCopy(mesh.vertexBuffer(), mapped.data());
            }

            GpuBuffer indices;
            VertexFormat.IndexType indexType;
            RenderSystem.AutoStorageIndexBuffer seq = RenderSystem.getSequentialBuffer(PIPELINE.getVertexFormatMode());
            indices = seq.getBuffer(state.indexCount());
            indexType = seq.type();
            var dynamicTransforms = RenderSystem.getDynamicUniforms().writeTransform(
                    RenderSystem.getModelViewMatrix(), WHITE, ZERO, IDENTITY);
            try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                    () -> "Mehmet Client ESP through walls",
                    client.getMainRenderTarget().getColorTextureView(), OptionalInt.empty(),
                    client.getMainRenderTarget().getDepthTextureView(), OptionalDouble.empty())) {
                pass.setPipeline(PIPELINE);
                RenderSystem.bindDefaultUniforms(pass);
                pass.setUniform("DynamicTransforms", dynamicTransforms);
                pass.setVertexBuffer(0, vertexBuffer.currentBuffer());
                pass.setIndexBuffer(indices, indexType);
                pass.drawIndexed(0, 0, state.indexCount(), 1);
            }
            vertexBuffer.rotate();
        } finally {
            mesh.close();
        }
    }

    private static void addBox(BufferBuilder out, Matrix4f matrix, Box b) {
        double x0=b.minX(), y0=b.minY(), z0=b.minZ(), x1=b.maxX(), y1=b.maxY(), z1=b.maxZ();
        double[][] p = {{x0,y0,z0},{x1,y0,z0},{x1,y1,z0},{x0,y1,z0},
                        {x0,y0,z1},{x1,y0,z1},{x1,y1,z1},{x0,y1,z1}};
        int[][] edges = {{0,1},{1,2},{2,3},{3,0},{4,5},{5,6},{6,7},{7,4},{0,4},{1,5},{2,6},{3,7}};
        float a=((b.color() >>> 24) & 255) / 255f;
        float r=((b.color() >>> 16) & 255) / 255f;
        float g=((b.color() >>> 8) & 255) / 255f;
        float bl=(b.color() & 255) / 255f;
        for (int[] edge : edges) {
            for (int i : edge) out.addVertex(matrix, (float)p[i][0], (float)p[i][1], (float)p[i][2]).setColor(r,g,bl,a);
        }
    }

    public static Box block(int x, int y, int z, int color) {
        return new Box(x + 0.015, y + 0.015, z + 0.015, x + 0.985, y + 0.985, z + 0.985, color);
    }

    public static Box entity(AABB b, int color) {
        return new Box(b.minX, b.minY, b.minZ, b.maxX, b.maxY, b.maxZ, color);
    }

    public record Box(double minX, double minY, double minZ, double maxX, double maxY, double maxZ, int color) {}
}
