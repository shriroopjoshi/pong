package com.shrp.pong;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;
import android.support.v4.content.res.ResourcesCompat;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.View;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;
import java.util.NavigableSet;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Created by shriroop on 06-Jun-17.
 */

public class GameThread extends Thread {

    private final SurfaceHolder _surfaceHolder;
    private Handler _handler;
    private boolean isRunning;

    private Ball ball;
    private Sprite topBat, bottomBat;
    private int score;
    private long scoreTime;

    private Bitmap background;
    private int canvasWidth, canvasHeight;
    private static final int STATE_PAUSE = 0;
    private static final int STATE_RUNNING = 4;

    private double delayTime = 0;
    private double frameTime = 0;
    private int currentState;
    private int previousState;
    private SharedPreferences prefs;

    GameThread(SurfaceHolder holder, Context context, Handler handler) {
        _surfaceHolder = holder;
        _handler = handler;
        prefs = context.getSharedPreferences("HIGHSCORE", Context.MODE_PRIVATE);

        Resources resources = context.getResources();
        //resources.getDrawable(R.drawable.ball);
        Drawable ball = ResourcesCompat.getDrawable(resources, R.drawable.ball, null);
        this.ball = new Ball(ball, canvasWidth, canvasHeight, new VelocityGenerator());
        this.ball.setInitialVelocity();
        this.ball.center();

        Drawable bat = ResourcesCompat.getDrawable(resources, R.drawable.bat, null);
        bottomBat = new Sprite(bat, canvasWidth, canvasHeight, 0, false);
        topBat = new Sprite(bat, canvasWidth, canvasHeight, 0, false);
        setInitialBatPosition();

        background = BitmapFactory.decodeResource(resources, R.drawable.background);

        score = 10;
        SoundManager.getInstance();
        SoundManager.initSounds(context);
        SoundManager.loadSounds();
    }

    private void setInitialBatPosition() {
        bottomBat.centerHorizontal();
        bottomBat.setYPosition(canvasHeight - (canvasHeight / 100 * 20));
        topBat.centerHorizontal();
        topBat.setYPosition(canvasHeight / 100 * 5);
    }

    void doStart() {
        synchronized (_surfaceHolder) {
            setState(STATE_RUNNING);
            resetGame();
        }
    }

    private void resetGame() {
        ball.center();
        topBat.centerHorizontal();
        topBat.setVelocity(new Velocity(0, 0));
        bottomBat.centerHorizontal();
        bottomBat.setVelocity(new Velocity(0, 0));
        setRunning(true);
        score = 10;
        scoreTime = -1 * System.currentTimeMillis();
        delayTime = 2;
    }

    @Override
    public void run() {
        long startTime = SystemClock.uptimeMillis();
        while(isRunning) {
            Canvas canvas = null;
            try {
                long currentTime = SystemClock.uptimeMillis();
                frameTime = (currentTime - startTime) / 1000.0;
                startTime = currentTime;
                canvas = _surfaceHolder.lockCanvas();
                synchronized (_surfaceHolder) {
                    if(currentState == GameThread.STATE_RUNNING && delayTime <= 0) {
                        checkCollision();
                        checkBallBounds();
                        advanceBall(frameTime);
                    }
                    if(delayTime > 0) {
                        delayTime = delayTime - frameTime;
                    }
                    drawScoreBoard();
                    doDraw(canvas);
                }
            } finally {
                if(canvas != null) {
                    _surfaceHolder.unlockCanvasAndPost(canvas);
                }
            }
        }
    }

    private void drawScoreBoard() {
        Message message = _handler.obtainMessage();
        Bundle bundle = new Bundle();
        String score = "Attempts left: " + this.score;
        bundle.putString("score", score);
        long time = (scoreTime + System.currentTimeMillis()) / 1000;
        bundle.putString("time", time + " sec");
        bundle.putInt("visibility", View.VISIBLE);
        message.setData(bundle);
        _handler.sendMessage(message);
    }

    private void advanceBall(double frameTime) {
        ball.move(frameTime);
        topBat.move(frameTime);
        bottomBat.move(frameTime);
    }

    private void checkBallBounds() {
        if(ball.isOutOfXBounds(frameTime)) {
            ball.reverseXVelocity();
            SoundManager.playSound(SoundManager.SOUND_HIT, 2);
        }
        if(ball.isOutOfYBounds(frameTime)) {
            if(score == 0) {
                finishGame();
            } else {
                scored(new Velocity(133, -93));
            }
        }
    }

    private void finishGame() {
        Message message = _handler.obtainMessage();
        Bundle bundle = new Bundle();
        long time = (scoreTime + System.currentTimeMillis()) / 1000;
        bundle.putString("time", "Time: " + time + " sec");
        bundle.putString("score", "Attempts left: " + score);
        bundle.putInt("visibility", View.VISIBLE);
        message.setData(bundle);
        _handler.sendMessage(message);
        SoundManager.playSound(SoundManager.SOUND_TERMINATOR, 1);
        setState(STATE_PAUSE);
        updateHighScore(time);
        resetGame();
    }

    private void updateHighScore(long time) {
        Map<String, ?> all = prefs.getAll();
        ArrayList<Long> arr = new ArrayList<>();
        for(String s: all.keySet()) {
            if(s.contains("highscore")) {
                arr.add((Long) all.get(s));
            }
        }
        TreeSet<Long> scores = new TreeSet<>(arr);
        scores.add(time);
        NavigableSet<Long> longs = scores.descendingSet();
        SharedPreferences.Editor editor = prefs.edit();
        for(String s: all.keySet()) {
            if(s.contains("highscore")) {
                editor.remove(s);
            }
        }
        for(int i = 1; i <= 10; i++) {
            if(longs.size() > 0) {
                long first = longs.first();
                editor.putLong("highscore" + i, first);
                longs.remove(first);
            }
        }
        editor.apply();
    }

    private void scored(Velocity velocity) {
        score--;
        ball.center();
        ball.setVelocity(velocity);
        SoundManager.playSound(SoundManager.SOUND_HIT, 1);
        delayTime = 1;
    }

    private void checkCollision() {
        if(bottomBat.collidesWith(ball, frameTime)) {
            ball.setPreviousLocation(frameTime);
            ball.generateNewVelocityUp();
            bottomBat.setPreviousLocation(frameTime);
            SoundManager.playSound(SoundManager.SOUND_HIT, 1);
        } else if(topBat.collidesWith(ball, frameTime)) {
            ball.setPreviousLocation(frameTime);
            ball.generateNewVelocityDown();
            topBat.setPreviousLocation(frameTime);
            SoundManager.playSound(SoundManager.SOUND_HIT, 1);
        }
    }

    void setSurfaceSize(int width, int height) {
        synchronized (_surfaceHolder) {
            canvasWidth = width;
            canvasHeight = height;

            ball.canvasChanges(width, height);
            topBat.canvasChanges(width, height);
            bottomBat.canvasChanges(width, height);
            ball.center();
            setInitialBatPosition();
        }
    }

    private void doDraw(Canvas canvas) {
        if(canvas != null) {
            canvas.drawBitmap(background, 0, 0, null);
            ball.draw(canvas);
            topBat.draw(canvas);
            bottomBat.draw(canvas);
        }
    }


    void setRunning(boolean running) {
        this.isRunning = running;
    }

    private void setState(int state) {
        this.currentState = state;
    }

    void pause() {
        synchronized (_surfaceHolder) {
            setState(STATE_PAUSE);
        }
    }

    public void unpause() {
        synchronized (_surfaceHolder) {
            if(currentState == STATE_PAUSE) {
                setState(previousState);
            }
        }
    }

    void resumeGame() {
        setState(previousState);
    }

    void pauseGame() {
    }

    void setBatPosition(float x1, float y1, float x2, float y2) {
        bottomBat.setXPosition((int) (x1 <= 0 ? 0: x1));
        if(bottomBat.isMaximumRight(canvasWidth)) {
            bottomBat.setMaximumRight(canvasWidth);
        }
        int x = canvasWidth - (int) x1  - topBat.getWidth();
        topBat.setXPosition(x <= 0 ? 0 : x);
        if(topBat.isMaximumRight(canvasWidth)) {
            topBat.setMaximumRight(canvasWidth);
        }

    }
}
