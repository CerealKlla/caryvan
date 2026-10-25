package com.github.cerealklla.caryvan.client.renderer.entity.model;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

/**
 * A small convenience wrapper around vanilla's own {@link CubeListBuilder}/{@link PartDefinition}
 * model-building API -- adapted from NiftyCarts' {@code EasyMeshBuilder} (MIT licensed, see this
 * repo's own CLAUDE.md). Ports essentially unchanged: this sits on top of vanilla's stable,
 * long-standing Java-defined-cuboid-model system (the same one every vanilla/modded
 * {@code ModelPart}-based entity already uses), which hasn't drifted the way this environment's
 * lower-level render-submission API has.
 */
final class EasyMeshBuilder {

    private final String name;
    private float x;
    private float y;
    private float z;
    float xRot;
    float yRot;
    float zRot;
    private final CubeListBuilder cubes;
    private final List<EasyMeshBuilder> children = new ArrayList<>();

    EasyMeshBuilder(final String name) {
        this.name = name;
        this.cubes = new CubeListBuilder();
    }

    EasyMeshBuilder(final String name, final int u, final int v) {
        this(name);
        this.cubes.texOffs(u, v);
    }

    EasyMeshBuilder addBox(final float x, final float y, final float z, final float width, final float height, final float depth) {
        this.cubes.addBox(x, y, z, width, height, depth);
        return this;
    }

    void addChild(final EasyMeshBuilder child) {
        this.children.add(child);
    }

    void setRotationPoint(final float x, final float y, final float z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    void build(final PartDefinition parent) {
        PartDefinition part = parent.addOrReplaceChild(this.name, this.cubes,
                PartPose.offsetAndRotation(this.x, this.y, this.z, this.xRot, this.yRot, this.zRot));
        for (final EasyMeshBuilder child : this.children) {
            child.build(part);
        }
    }

    static LayerDefinition rootLayer(final EasyMeshBuilder root, final int texWidth, final int texHeight) {
        final MeshDefinition mesh = new MeshDefinition();
        root.build(mesh.getRoot());
        return LayerDefinition.create(mesh, texWidth, texHeight);
    }
}
