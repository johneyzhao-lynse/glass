package com.wj.glasses.rtsp;

import android.view.SurfaceView;

import androidx.annotation.NonNull;

import com.pedro.common.ConnectChecker;
import com.pedro.common.VideoCodec;
import com.pedro.rtspserver.server.ClientListener;
import com.pedro.rtspserver.server.ServerClient;
import com.wj.glasses.utils.GlassesLog;

public class RtspMgr {

    private static final String TAG = "RtspMgr: ";

    private static final int RTSP_PORT = 5555;

    private RtspCamera rtspCamera;

    public RtspMgr() {
    }

    public static RtspMgr getInstance() {
        return RtspMgr.SingletonInstance.INSTANCE;
    }

    private static class SingletonInstance {
        private static final RtspMgr INSTANCE = new RtspMgr();
    }

    public boolean startRtsp(SurfaceView sf) {
        if (isStreaming()) return true;
        GlassesLog.d(TAG + "startRtsp");
        rtspCamera = new RtspCamera(sf, connectChecker, RTSP_PORT);
//        rtspServerCamera1.getStreamClient().forceIpType(IpType.IPv4);

        rtspCamera.getStreamClient().setClientListener(clientListener);
        if (!rtspCamera.isOnPreview()) {
            GlassesLog.d(TAG + "startPreview");
            rtspCamera.startPreview();
        }
        return startStream();
    }


    public boolean startStream() {
        rtspCamera.setVideoCodec(VideoCodec.H265);
        boolean prepared = rtspCamera.prepareAudio() && rtspCamera.prepareVideo(1280, 720, 30, 1200 * 1024, 180);
        if (!prepared) {
            GlassesLog.d(TAG + "Error preparing stream, This device cant do it");
            return false;
        }
        rtspCamera.startStream();
        return true;

//        if (rtspServerCamera1.isStreaming()) {
//            rtspServerCamera1.getStreamClient().forceIpType(IpType.IPv4);
//        }
    }

    public void stopRtsp() {
        GlassesLog.d(TAG + "startRtsp");
        if (isStreaming()) {
            rtspCamera.stopStream();
            if (rtspCamera.isOnPreview()) {
                rtspCamera.stopPreview();
            }
        }
    }

    public String getUri() {
        if (rtspCamera != null) {
            GlassesLog.d(TAG + " getUri ");
            return rtspCamera.getStreamClient().getEndPointConnection();
        }
        return null;
    }

    public boolean isStreaming() {
        if (rtspCamera != null) {
            return rtspCamera.isStreaming();
        }
        return false;
    }

    ConnectChecker connectChecker = new ConnectChecker() {
        @Override
        public void onConnectionStarted(@NonNull String s) {
            GlassesLog.e("rtsp onConnectionStarted: + " + s);
        }

        @Override
        public void onConnectionSuccess() {
            GlassesLog.e("rtsp onConnectionSuccess: + ");
        }

        @Override
        public void onConnectionFailed(@NonNull String s) {
            GlassesLog.e("rtsp onConnectionFailed: + " + s);
            rtspCamera.stopStream();
        }

        @Override
        public void onDisconnect() {
            GlassesLog.d("rtsp onDisconnect");
        }

        @Override
        public void onAuthError() {
            GlassesLog.e("rtsp onAuthError: ");
            rtspCamera.stopStream();
        }

        @Override
        public void onAuthSuccess() {

        }
    };

    ClientListener clientListener = new ClientListener() {
        @Override
        public void onClientConnected(@NonNull ServerClient serverClient) {
            GlassesLog.d("rtsp onClientConnected");
        }

        @Override
        public void onClientDisconnected(@NonNull ServerClient serverClient) {
            GlassesLog.d("rtsp onClientDisconnected");
        }

        @Override
        public void onClientNewBitrate(long l, @NonNull ServerClient serverClient) {
            GlassesLog.d("rtsp onClientNewBitrate");
        }
    };
}
