package com.miseru.avemujica.client;

import com.miseru.avemujica.block.PhotoPosterBlock;
import com.miseru.avemujica.block.PhotoPosterBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;

/** Wide/multiblock photo stand: draws a frame, and samples ONLY the chosen photo crop. */
public final class PhotoPosterRenderer implements BlockEntityRenderer<PhotoPosterBlockEntity> {
    private static final ResourceLocation FRAME=ResourceLocation.withDefaultNamespace("textures/block/dark_oak_planks.png");
    private static final ResourceLocation DEFAULT_ART=ResourceLocation.fromNamespaceAndPath("avemujica","textures/block/merch_poster_art.png");
    public PhotoPosterRenderer(BlockEntityRendererProvider.Context context) { }
    @Override public boolean shouldRenderOffScreen(PhotoPosterBlockEntity tile) { return true; }
    @Override public int getViewDistance() { return 128; }
    @Override public void render(PhotoPosterBlockEntity tile,float tickDelta,PoseStack stack,
                                 MultiBufferSource buffers,int light,int overlay) {
        int w=tile.widthBlocks(), h=tile.heightBlocks();
        PhotoPosterTextures.PhotoTexture photo=null;
        if (tile.hasPhoto()) {
            photo=PhotoPosterTextures.find(tile.owner(),tile.photoId());
            if (photo==null) PhotoPosterTextures.request(tile.getBlockPos(),tile.owner(),tile.photoId());
        }
        // Always render a full-bright art overlay, even on an unbound 1x1 poster.
        // The normal baked block model still supplies its unlit wood and default art.
        Direction facing=tile.getBlockState().getValue(PhotoPosterBlock.FACING);
        int yaw=switch (facing) { case EAST->90;case SOUTH->180;case WEST->270;default->0; };
        stack.pushPose();
        stack.translate(.5,0,.5);
        stack.mulPose(Axis.YP.rotationDegrees(yaw));
        stack.translate(-.5,0,-.5);

        float left=PosterCrop.apertureLeft(w,h),right=PosterCrop.apertureRight(w,h);
        float bottom=PosterCrop.apertureBottom(w,h),top=PosterCrop.apertureTop(w,h);
        // In multi-block mode the regular one-block model is hidden, so render
        // an actual six-sided 3D frame here, not paper-thin border quads. The
        // rails use the original dark-oak texture on every visible surface.
        if (w>1 || h>1) {
            VertexConsumer frame=buffers.getBuffer(RenderType.entitySolid(FRAME));
            for (PosterFrameGeometry.Cuboid piece : PosterFrameGeometry.build(w,h))
                cuboid(frame,stack,piece,light);
        }
        ResourceLocation texture=photo==null?DEFAULT_ART:photo.id();
        PosterCrop.Region crop=photo==null
                ? PosterCrop.calculate(64,64,PosterCrop.apertureAspect(w,h),1F,.5F,.5F)
                : PosterCrop.calculate(photo.width(),photo.height(),PosterCrop.apertureAspect(w,h),
                        tile.cropZoom(),tile.cropCenterX(),tile.cropCenterY());
        VertexConsumer art=buffers.getBuffer(RenderType.entityTranslucent(texture));
        // On single block, original model art lies at 6.7/16 and 9.3/16.
        float front=(w==1&&h==1)?6.69F/16F:PosterFrameGeometry.FRONT_ART;
        float back=(w==1&&h==1)?9.31F/16F:PosterFrameGeometry.BACK_ART;
        // Full brightness is an art-surface property; retain world lighting for the timber.
        panel(art,stack,left,right,bottom,top,front,LightTexture.FULL_BRIGHT,false,crop.u0(),crop.v0(),crop.u1(),crop.v1());
        panel(art,stack,left,right,bottom,top,back,LightTexture.FULL_BRIGHT,true,crop.u0(),crop.v0(),crop.u1(),crop.v1());
        stack.popPose();
    }
    /** Build every visible face of each wooden rail in local billboard coordinates. */
    private static void cuboid(VertexConsumer out, PoseStack pose,
                               PosterFrameGeometry.Cuboid b, int light) {
        float x0=b.x0(), x1=b.x1(), y0=b.y0(), y1=b.y1(), z0=b.z0(), z1=b.z1();
        // All normals and windings point outward; entitySolid can safely cull
        // the hidden faces while retaining the front, back and side thickness.
        face(out,pose,light, 0,0,-1,
                x0,y1,z0, x1,y1,z0, x1,y0,z0, x0,y0,z0);
        face(out,pose,light, 0,0,1,
                x1,y1,z1, x0,y1,z1, x0,y0,z1, x1,y0,z1);
        face(out,pose,light, 0,1,0,
                x0,y1,z1, x1,y1,z1, x1,y1,z0, x0,y1,z0);
        face(out,pose,light, 0,-1,0,
                x0,y0,z0, x1,y0,z0, x1,y0,z1, x0,y0,z1);
        face(out,pose,light, -1,0,0,
                x0,y0,z0, x0,y0,z1, x0,y1,z1, x0,y1,z0);
        face(out,pose,light, 1,0,0,
                x1,y0,z1, x1,y0,z0, x1,y1,z0, x1,y1,z1);
    }

    private static void face(VertexConsumer out, PoseStack pose, int light,
                             float nx,float ny,float nz,
                             float ax,float ay,float az, float bx,float by,float bz,
                             float cx,float cy,float cz, float dx,float dy,float dz) {
        woodVertex(out,pose,light,ax,ay,az,0,0,nx,ny,nz);
        woodVertex(out,pose,light,bx,by,bz,1,0,nx,ny,nz);
        woodVertex(out,pose,light,cx,cy,cz,1,1,nx,ny,nz);
        woodVertex(out,pose,light,dx,dy,dz,0,1,nx,ny,nz);
    }
    private static void woodVertex(VertexConsumer out,PoseStack pose,int light,
                                   float x,float y,float z,float u,float v,
                                   float nx,float ny,float nz) {
        out.addVertex(pose.last().pose(),x,y,z).setColor(255,255,255,255)
                .setUv(u,v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light)
                .setNormal(pose.last(),nx,ny,nz);
    }

    private static void panel(VertexConsumer out,PoseStack pose,float l,float r,float b,float t,
                              float z,int light,boolean back,float u0,float v0,float u1,float v1) {
        // Mirrored UV on rear face ensures the image is legible from both sides.
        if (!back) {
            vertex(out,pose,l,t,z,u0,v0,light,false);
            vertex(out,pose,r,t,z,u1,v0,light,false);
            vertex(out,pose,r,b,z,u1,v1,light,false);
            vertex(out,pose,l,b,z,u0,v1,light,false);
        } else {
            vertex(out,pose,l,b,z,u1,v1,light,true);
            vertex(out,pose,r,b,z,u0,v1,light,true);
            vertex(out,pose,r,t,z,u0,v0,light,true);
            vertex(out,pose,l,t,z,u1,v0,light,true);
        }
    }
    private static void vertex(VertexConsumer out,PoseStack pose,float x,float y,float z,
                               float u,float v,int light,boolean back) {
        out.addVertex(pose.last().pose(),x,y,z).setColor(255,255,255,255)
                .setUv(u,v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light)
                .setNormal(pose.last(),0F,0F,back?1F:-1F);
    }
}
