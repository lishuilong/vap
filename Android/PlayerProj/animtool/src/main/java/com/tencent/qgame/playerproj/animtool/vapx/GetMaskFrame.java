package com.tencent.qgame.playerproj.animtool.vapx;

import com.tencent.qgame.playerproj.animtool.CommonArg;
import com.tencent.qgame.playerproj.animtool.TLog;
import com.tencent.qgame.playerproj.animtool.data.PointRect;

import java.awt.image.BufferedImage;
import java.io.File;

import javax.imageio.ImageIO;

/**
 * 获取融合动画遮罩
 */
public class GetMaskFrame {

    private static final String TAG = "GetMaskFrame";

    public FrameSet.FrameObj getFrameObj(int frameIndex, CommonArg commonArg, int[] outputArgb) throws Exception {

        FrameSet.FrameObj frameObj = new FrameSet.FrameObj();
        frameObj.frameIndex = frameIndex;

        FrameSet.Frame frame;
        // 需要放置的位置
        int x;
        int y;
        int gap = commonArg.gap;
        if (commonArg.isVLayout) {
            x = commonArg.alphaPoint.w + gap;
            y = commonArg.alphaPoint.y;
        } else {
            x = commonArg.alphaPoint.x;
            y = commonArg.alphaPoint.h + gap;
        }
        int startX = x;
        int lastMaxY = y;
        for (int i=0; i<commonArg.srcSet.srcs.size(); i++) {
            frame = getFrame(frameIndex, commonArg.srcSet.srcs.get(i), outputArgb, commonArg.outputW, commonArg.outputH, x, y, startX, lastMaxY);
            if (frame == null) continue;
            // 计算下一个遮罩起点
            x = frame.mFrame.x + frame.mFrame.w + gap;
            y = frame.mFrame.y;
            int newY = frame.mFrame.y + frame.mFrame.h + gap;
            if (newY > lastMaxY) {
                lastMaxY = newY;
            }

            frameObj.frames.add(frame);
        }

        if (frameObj.frames.isEmpty()) {
            return null;
        }
        return frameObj;
    }


    private FrameSet.Frame getFrame(int frameIndex, SrcSet.Src src, int[] outputArgb, int outW, int outH, int x, int y, int startX, int lastMaxY) throws Exception {
        File inputFile = new File(src.srcPath  + String.format("%03d", frameIndex)+".png");
        if (!inputFile.exists()) {
            return null;
        }

        BufferedImage inputBuf = ImageIO.read(inputFile);
        int maskW = inputBuf.getWidth();
        int maskH = inputBuf.getHeight();
        int[] maskArgb = inputBuf.getRGB(0, 0, maskW, maskH, null, 0, maskW);

        FrameSet.Frame frame = new FrameSet.Frame();
        frame.srcId = src.srcId;
        frame.z = src.z;

        frame.frame = getSrcFramePoint(maskArgb, maskW, maskH);
        if (frame.frame == null) {
            // 有文件，但内容是空
            return null;
        }

        PointRect mFrame = new PointRect(x, y, frame.frame.w, frame.frame.h);
        // 计算是否能放下遮罩
        if (mFrame.x + mFrame.w > outW) { // 超宽换行
            mFrame.x = startX;
            mFrame.y = lastMaxY;
            if (mFrame.x + mFrame.w > outW) {
                TLog.i(TAG, "Error: frameIndex=" + frameIndex + ",src=" + src.srcId + ", no more space for(w)" + mFrame);
                return null;
            }
        }
        if (mFrame.y + mFrame.h > outH) { // 高度不够直接错误
            TLog.i(TAG, "Error: frameIndex=" + frameIndex + ",src=" + src.srcId + ", no more space(h)" + mFrame);
            return null;
        }
        frame.mFrame = mFrame;

        fillMaskToOutput(outputArgb, outW, maskArgb, maskW, frame.frame, frame.mFrame, SrcSet.Src.SRC_TYPE_TXT.equals(src.srcType));

        // 设置src的w,h 取所有遮罩里最大值
        synchronized (GetMaskFrame.class) {
            // 只按宽度进行判断防止横跳
            if (frame.frame.w > src.w) {
                src.w = frame.frame.w;
                src.h = frame.mFrame.h;
            }
        }
        return frame;
    }


    /**
     * 获取遮罩位置信息 并转换为黑白
     */
    private PointRect getSrcFramePoint(int[] maskArgb, int w, int h) {

        PointRect point = new PointRect();

        int startX = -1;
        int startY = -1;
        int maxX = 0;
        int maxY = 0;
        for (int y=0; y<h; y++) {
            for (int x = 0; x < w; x++) {
                int alpha = maskArgb[x + y*w] >>> 24;
                if (alpha > 0) {
                    if (startX == -1 || startY == -1) {
                        startX = x;
                        startY = y;
                    }
                    if (x > maxX) maxX = x;
                    if (y > maxY) maxY = y;
                }
            }
        }

        point.x = startX;
        point.y = startY;
        point.w = maxX - startX;
        point.h = maxY - startY;
        if (point.x == -1 || point.y == -1 || point.w <=0 || point.h <= 0) return null;

        return point;

    }


    private void fillMaskToOutput(int[] outputArgb, int outW,
                                  int[] maskArgb, int maskW,
                                  PointRect frame,
                                  PointRect mFrame,
                                  boolean isTxtMask) {
        for (int y=0; y < frame.h; y++) {
            for (int x=0; x < frame.w; x++) {
                int maskXOffset = frame.x;
                int maskYOffset = frame.y;
                // 先从遮罩 maskArgb 取色
                int maskColor = maskArgb[x + maskXOffset + (y + maskYOffset) * maskW];
                int alpha = maskColor >>> 24;
                // 文字mask 黑色部分不遮挡，红色部分被遮挡
                if (isTxtMask) {
                    int maskRed = (maskColor & 0x00ff0000) >>> 16;
                    alpha = 255 - maskRed; // 红色部分算遮挡
                }
                // 最终color
                int color = 0xff000000 + (alpha << 16) + (alpha << 8) + alpha;

                // 将遮罩颜色放置到视频中对应区域
                int outputXOffset = mFrame.x;
                int outputYOffset = mFrame.y;
                outputArgb[x + outputXOffset + (y + outputYOffset) * outW] = color;

            }
        }
    }

}
