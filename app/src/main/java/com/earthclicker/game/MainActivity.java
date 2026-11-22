package com.earthclicker.game;

import android.app.Activity;
import android.os.Bundle;
import android.opengl.GLSurfaceView;
import android.opengl.GLES20;
import android.view.MotionEvent;
import android.widget.*;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.content.SharedPreferences;
import android.widget.FrameLayout;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;
import java.util.Random;

public class MainActivity extends Activity {

    private GLSurfaceView glView;
    private EarthRenderer renderer;

    private TextView coinsText, clicksText, powerText;

    private int coins;
    private int clicks;
    private int power;
    private int upgradeCost;

    private SharedPreferences save;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);

        save = getSharedPreferences("earth_save", MODE_PRIVATE);

        coins = save.getInt("coins", 0);
        clicks = save.getInt("clicks", 0);
        power = save.getInt("power", 1);
        upgradeCost = save.getInt("cost", 50);

        FrameLayout root = new FrameLayout(this);

        glView = new GLSurfaceView(this);
        glView.setEGLContextClientVersion(2);

        renderer = new EarthRenderer();
        glView.setRenderer(renderer);
        glView.setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);

        root.addView(glView);

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(20, 30, 20, 20);
        panel.setGravity(Gravity.CENTER_HORIZONTAL);
        panel.setBackgroundColor(Color.argb(170, 5, 15, 40));

        coinsText = new TextView(this);
        clicksText = new TextView(this);
        powerText = new TextView(this);

        coinsText.setTextColor(Color.WHITE);
        clicksText.setTextColor(Color.WHITE);
        powerText.setTextColor(Color.WHITE);

        coinsText.setTextSize(23);
        clicksText.setTextSize(17);
        powerText.setTextSize(17);

        panel.addView(coinsText);
        panel.addView(clicksText);
        panel.addView(powerText);

        FrameLayout.LayoutParams topParams =
            new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP
            );

        root.addView(panel, topParams);

        Button upgrade = new Button(this);
        upgrade.setText("УЛУЧШИТЬ СИЛУ КЛИКА");
        upgrade.setOnClickListener(v -> buyUpgrade());

        FrameLayout.LayoutParams bottomParams =
            new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM
            );

        root.addView(upgrade, bottomParams);

        glView.setOnTouchListener(new View.OnTouchListener() {
            float lastX, lastY;
            boolean moved;

            @Override
            public boolean onTouch(View v, MotionEvent e) {
                switch (e.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        lastX = e.getX();
                        lastY = e.getY();
                        moved = false;
                        return true;

                    case MotionEvent.ACTION_MOVE:
                        float dx = e.getX() - lastX;
                        float dy = e.getY() - lastY;

                        if (Math.abs(dx) + Math.abs(dy) > 5) {
                            moved = true;
                            final float x = dx;
                            final float y = dy;
                            glView.queueEvent(() -> renderer.rotate(x, y));
                        }

                        lastX = e.getX();
                        lastY = e.getY();
                        return true;

                    case MotionEvent.ACTION_UP:
                        if (!moved) {
                            clickEarth();
                        }
                        return true;
                }

                return true;
            }
        });

        setContentView(root);
        updateUI();
    }

    private void clickEarth() {
        clicks++;
        coins += power;

        if (clicks % 100 == 0) {
            glView.queueEvent(() -> renderer.addTree());

            Toast.makeText(
                this,
                "Новое дерево появилось на Земле!",
                Toast.LENGTH_SHORT
            ).show();
        }

        saveGame();
        updateUI();
    }

    private void buyUpgrade() {
        if (coins >= upgradeCost) {
            coins -= upgradeCost;
            power++;
            upgradeCost *= 2;

            saveGame();
            updateUI();

            Toast.makeText(
                this,
                "Сила клика увеличена!",
                Toast.LENGTH_SHORT
            ).show();
        } else {
            Toast.makeText(
                this,
                "Недостаточно Земных Монет!",
                Toast.LENGTH_SHORT
            ).show();
        }
    }

    private void updateUI() {
        coinsText.setText("Земные монеты: " + coins);
        clicksText.setText("Клики: " + clicks);
        powerText.setText(
            "Сила клика: " + power +
            " | Улучшение: " + upgradeCost + " монет"
        );
    }

    private void saveGame() {
        save.edit()
            .putInt("coins", coins)
            .putInt("clicks", clicks)
            .putInt("power", power)
            .putInt("cost", upgradeCost)
            .apply();
    }

    @Override
    protected void onPause() {
        super.onPause();

        if (glView != null) {
            glView.onPause();
        }

        saveGame();
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (glView != null) {
            glView.onResume();
        }
    }

    private class EarthRenderer implements GLSurfaceView.Renderer {

        private int program;

        private FloatBuffer vertices;
        private ShortBuffer indices;
        private FloatBuffer treeBuffer;

        private int indexCount;
        private int treeCount = 0;

        private float angle = 0;
        private float tilt = 0;

        private final String vertexShader =
            "attribute vec3 aPosition;" +
            "uniform float uAngle;" +
            "uniform float uTilt;" +
            "varying vec3 vPos;" +
            "void main() {" +
            "float c = cos(uAngle);" +
            "float s = sin(uAngle);" +
            "vec3 p = vec3(" +
            "aPosition.x*c-aPosition.z*s," +
            "aPosition.y," +
            "aPosition.x*s+aPosition.z*c);" +
            "float ct = cos(uTilt);" +
            "float st = sin(uTilt);" +
            "p = vec3(p.x,p.y*ct-p.z*st,p.y*st+p.z*ct);" +
            "vPos = aPosition;" +
            "gl_Position = vec4(p.x*0.85,p.y*0.85,p.z*0.3,1.0);" +
            "gl_PointSize = 13.0;" +
            "}";

        private final String fragmentShader =
            "precision mediump float;" +
            "uniform int uPoint;" +
            "varying vec3 vPos;" +
            "void main() {" +
            "if(uPoint == 1) {" +
            "vec2 q = gl_PointCoord-vec2(0.5);" +
            "if(dot(q,q)>0.25) discard;" +
            "gl_FragColor = vec4(0.12,0.9,0.2,1.0);" +
            "return;" +
            "}" +
            "vec3 p = normalize(vPos);" +
            "float land = sin(p.x*13.0+sin(p.z*9.0))*" +
            "sin(p.y*12.0+p.z*7.0);" +
            "vec3 water = vec3(0.015,0.12,0.48);" +
            "vec3 ground = vec3(0.12,0.48,0.16);" +
            "vec3 color = mix(water,ground,step(0.15,land));" +
            "vec3 light = normalize(vec3(-0.5,0.4,1.0));" +
            "float shade = max(dot(p,light),0.0)*0.7+0.3;" +
            "color *= shade;" +
            "gl_FragColor = vec4(color,1.0);" +
            "}";

        @Override
        public void onSurfaceCreated(
            javax.microedition.khronos.opengles.GL10 gl,
            javax.microedition.khronos.egl.EGLConfig config) {

            GLES20.glClearColor(0.005f, 0.008f, 0.03f, 1f);
            GLES20.glEnable(GLES20.GL_DEPTH_TEST);

            int vs = loadShader(GLES20.GL_VERTEX_SHADER, vertexShader);
            int fs = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentShader);

            program = GLES20.glCreateProgram();
            GLES20.glAttachShader(program, vs);
            GLES20.glAttachShader(program, fs);
            GLES20.glLinkProgram(program);

            createSphere();
            updateTrees();
        }

        @Override
        public void onSurfaceChanged(
            javax.microedition.khronos.opengles.GL10 gl,
            int width,
            int height) {

            GLES20.glViewport(0, 0, width, height);
        }

        @Override
        public void onDrawFrame(
            javax.microedition.khronos.opengles.GL10 gl) {

            GLES20.glClear(
                GLES20.GL_COLOR_BUFFER_BIT |
                GLES20.GL_DEPTH_BUFFER_BIT
            );

            GLES20.glUseProgram(program);

            int pos = GLES20.glGetAttribLocation(program, "aPosition");
            int angleLoc = GLES20.glGetUniformLocation(program, "uAngle");
            int tiltLoc = GLES20.glGetUniformLocation(program, "uTilt");
            int pointLoc = GLES20.glGetUniformLocation(program, "uPoint");

            GLES20.glUniform1f(angleLoc, angle);
            GLES20.glUniform1f(tiltLoc, tilt);

            GLES20.glUniform1i(pointLoc, 0);

            GLES20.glEnableVertexAttribArray(pos);

            vertices.position(0);

            GLES20.glVertexAttribPointer(
                pos, 3, GLES20.GL_FLOAT,
                false, 12, vertices
            );

            GLES20.glDrawElements(
                GLES20.GL_TRIANGLES,
                indexCount,
                GLES20.GL_UNSIGNED_SHORT,
                indices
            );

            if (treeCount > 0 && treeBuffer != null) {
                GLES20.glUniform1i(pointLoc, 1);

                treeBuffer.position(0);

                GLES20.glVertexAttribPointer(
                    pos, 3, GLES20.GL_FLOAT,
                    false, 12, treeBuffer
                );

                GLES20.glDrawArrays(
                    GLES20.GL_POINTS, 0, treeCount
                );
            }

            GLES20.glDisableVertexAttribArray(pos);

            angle += 0.003f;
        }

        void rotate(float x, float y) {
            angle += x * 0.01f;
            tilt += y * 0.01f;

            if (tilt > 1.2f) tilt = 1.2f;
            if (tilt < -1.2f) tilt = -1.2f;
        }

        void addTree() {
            treeCount++;
            updateTrees();
        }

        void updateTrees() {
            float[] data = new float[treeCount * 3];
            Random random = new Random(777);

            for (int i = 0; i < treeCount; i++) {
                float y = random.nextFloat() * 2f - 1f;
                float a = random.nextFloat() * 6.28318f;
                float r = (float)Math.sqrt(1f - y*y);

                data[i*3] = r * (float)Math.cos(a) * 1.01f;
                data[i*3+1] = y * 1.01f;
                data[i*3+2] = r * (float)Math.sin(a) * 1.01f;
            }

            ByteBuffer bb = ByteBuffer.allocateDirect(data.length * 4);
            bb.order(ByteOrder.nativeOrder());

            treeBuffer = bb.asFloatBuffer();
            treeBuffer.put(data);
            treeBuffer.position(0);
        }

        void createSphere() {
            int lat = 40;
            int lon = 40;

            float[] v = new float[(lat+1)*(lon+1)*3];
            short[] idx = new short[lat*lon*6];

            int n = 0;

            for (int i = 0; i <= lat; i++) {
                float theta = (float)(Math.PI*i/lat);

                for (int j = 0; j <= lon; j++) {
                    float phi = (float)(2*Math.PI*j/lon);

                    v[n++] = (float)(Math.sin(theta)*Math.cos(phi));
                    v[n++] = (float)Math.cos(theta);
                    v[n++] = (float)(Math.sin(theta)*Math.sin(phi));
                }
            }

            n = 0;

            for (int i = 0; i < lat; i++) {
                for (int j = 0; j < lon; j++) {
                    short a = (short)(i*(lon+1)+j);
                    short b = (short)(a+lon+1);

                    idx[n++] = a;
                    idx[n++] = b;
                    idx[n++] = (short)(a+1);

                    idx[n++] = (short)(a+1);
                    idx[n++] = b;
                    idx[n++] = (short)(b+1);
                }
            }

            indexCount = idx.length;

            ByteBuffer vb = ByteBuffer.allocateDirect(v.length*4);
            vb.order(ByteOrder.nativeOrder());

            vertices = vb.asFloatBuffer();
            vertices.put(v);
            vertices.position(0);

            ByteBuffer ib = ByteBuffer.allocateDirect(idx.length*2);
            ib.order(ByteOrder.nativeOrder());

            indices = ib.asShortBuffer();
            indices.put(idx);
            indices.position(0);
        }

        private int loadShader(int type, String code) {
            int shader = GLES20.glCreateShader(type);
            GLES20.glShaderSource(shader, code);
            GLES20.glCompileShader(shader);
            return shader;
        }
    }
}
