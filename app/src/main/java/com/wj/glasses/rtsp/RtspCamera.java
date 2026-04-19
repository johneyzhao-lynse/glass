package com.wj.glasses.rtsp;

import android.media.MediaCodec;
import android.os.Build;
import android.view.SurfaceView;
import android.view.TextureView;

import androidx.annotation.RequiresApi;

import com.pedro.common.AudioCodec;
import com.pedro.common.ConnectChecker;
import com.pedro.common.VideoCodec;
import com.pedro.library.base.Camera1Base;
import com.pedro.library.util.streamclient.StreamBaseClient;
import com.pedro.library.view.OpenGlView;
import com.pedro.rtspserver.server.RtspServer;
import com.pedro.rtspserver.util.RtspServerStreamClient;

import java.nio.ByteBuffer;

public class RtspCamera extends Camera1Base {

    private RtspServer rtspServer;


    public RtspCamera(SurfaceView surfaceView, ConnectChecker connectChecker, int port) {
        super(surfaceView);
        rtspServer = new RtspServer(connectChecker, port);
        rtspServer.setLogs(false);
    }

    public RtspCamera(TextureView textureView, ConnectChecker connectChecker, int port) {
        super(textureView);
        rtspServer = new RtspServer(connectChecker, port);
        rtspServer.setLogs(false);
    }

    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
    public RtspCamera(OpenGlView openGlView, ConnectChecker connectChecker, int port) {
        super(openGlView);
        rtspServer = new RtspServer(connectChecker, port);
        rtspServer.setLogs(false);
    }

    void startStream() {
        super.startStream("");
    }


    @Override
    protected void onAudioInfoImp(boolean isStereo, int sampleRate) {
        rtspServer.setAudioInfo(sampleRate, isStereo);
    }

    @Override
    protected void startStreamImp(String url) {
        rtspServer.startServer();
    }

    @Override
    protected void stopStreamImp() {
        rtspServer.stopServer();
    }

    @Override
    protected void getAudioDataImp(ByteBuffer audioBuffer, MediaCodec.BufferInfo info) {
        rtspServer.sendAudio(audioBuffer, info);
    }

    @Override
    protected void onVideoInfoImp(ByteBuffer sps, ByteBuffer pps, ByteBuffer vps) {
        ByteBuffer newSps = sps.duplicate();
        ByteBuffer newPps = pps == null ? null : pps.duplicate();
        ByteBuffer newVps = vps == null ? null : vps.duplicate();
        rtspServer.setVideoInfo(newSps, newPps, newVps);
    }

    @Override
    protected void getVideoDataImp(ByteBuffer videoBuffer, MediaCodec.BufferInfo info) {
        rtspServer.sendVideo(videoBuffer, info);
    }

    @Override
    public RtspServerStreamClient getStreamClient() {
        return new RtspServerStreamClient(rtspServer);
    }

    @Override
    protected void setVideoCodecImp(VideoCodec codec) {
        rtspServer.setVideoCodec(codec);
    }

    @Override
    protected void setAudioCodecImp(AudioCodec codec) {
        rtspServer.setAudioCodec(codec);
    }
}
