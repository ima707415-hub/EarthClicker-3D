package com.earthclicker.game;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
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
    private TextView coinsText, clicksText, powerText, incomeText;
    private Button upgradeButton;
    private int coins, clicks, power, coinsPerSecond;
    private final int[] shopCosts = {50, 100, 250, 500};
    private final int[] shopLevels = {0, 0, 0, 0};
    private SharedPreferences save;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private final String[] shopNames = {
        "Улучшение клика",
        "Атом",
        "Лес",
        "Спутник"
    };
    private final String[] shopEffects = {
        "+1 к силе каждого клика",
        "+1 к силе клика и +1 монета/сек",
        "+2 монеты/сек",
        "+5 монет/сек"
    };

    private final Runnable incomeTick = new Runnable() {
        @Override
        public void run() {
            if (coinsPerSecond > 0) {
                coins += coinsPerSecond;
                saveGame();
                updateUI();
            }
            handler.postDelayed(this, 1000);
        }
    };

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        save = getSharedPreferences("earth_save", MODE_PRIVATE);
        coins = save.getInt("coins", 0);
        clicks = save.getInt("clicks", 0);
        power = save.getInt("power", 1);
        coinsPerSecond = save.getInt("coins_per_second", 0);
        for (int i = 0; i < shopCosts.length; i++) {
            shopCosts[i] = save.getInt("shop_cost_" + i, shopCosts[i]);
            shopLevels[i] = save.getInt("shop_level_" + i, 0);
        }

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
        panel.setBackgroundColor(Color.argb(175, 5, 15, 40));

        coinsText = makeText(23);
        clicksText = makeText(17);
        powerText = makeText(17);
        incomeText = makeText(17);
        panel.addView(coinsText);
        panel.addView(clicksText);
        panel.addView(powerText);
        panel.addView(incomeText);

        FrameLayout.LayoutParams topParams = new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.TOP
        );
        root.addView(panel, topParams);

        LinearLayout bottomPanel = new LinearLayout(this);
        bottomPanel.setOrientation(LinearLayout.HORIZONTAL);
        bottomPanel.setPadding(8, 6, 8, 8);
        bottomPanel.setBackgroundColor(Color.argb(190, 5, 15, 40));

        upgradeButton = new Button(this);
        upgradeButton.setOnClickListener(v -> buyQuickUpgrade());
        Button shopButton = new Button(this);
        shopButton.setText("МАГАЗИН");
        shopButton.setOnClickListener(v -> openShop());

        bottomPanel.addView(upgradeButton, new LinearLayout.LayoutParams(0,
            LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        bottomPanel.addView(shopButton, new LinearLayout.LayoutParams(0,
            LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        FrameLayout.LayoutParams bottomParams = new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM
        );
        root.addView(bottomPanel, bottomParams);

        glView.setOnTouchListener(new View.OnTouchListener() {
            float lastX, lastY;
            float downX, downY;
            boolean moved;
            @Override
            public boolean onTouch(View v, MotionEvent e) {
                switch (e.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        lastX = downX = e.getX();
                        lastY = downY = e.getY();
                        moved = false;
                        return true;

                    case MotionEvent.ACTION_MOVE:
                        float dx = e.getX() - lastX;
                        float dy = e.getY() - lastY;

                        // Rotate for every small finger movement. Do not apply
                        // a per-event threshold: Android often sends many tiny deltas.
                        if (dx != 0f || dy != 0f) {
                            final float dragX = dx;
                            final float dragY = dy;
                            glView.queueEvent(() -> renderer.rotate(dragX, dragY));
                        }

                        if (Math.abs(e.getX() - downX) + Math.abs(e.getY() - downY) > 10f) {
                            moved = true;
                        }

                        lastX = e.getX();
                        lastY = e.getY();
                        return true;

                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        if (e.getAction() == MotionEvent.ACTION_UP && !moved) {
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

    private TextView makeText(int size) {
        TextView text = new TextView(this);
        text.setTextColor(Color.WHITE);
        text.setTextSize(size);
        text.setGravity(Gravity.CENTER);
        return text;
    }

    private void clickEarth() {
        clicks++;
        coins += power;
        if (clicks % 100 == 0) {
            glView.queueEvent(() -> renderer.addTree());
            Toast.makeText(this, "Новое дерево появилось на Земле!", Toast.LENGTH_SHORT).show();
        }
        saveGame();
        updateUI();
    }

    private void buyQuickUpgrade() {
        buyItem(0, null, null);
    }

    private void openShop() {
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(24, 8, 24, 8);
        Button[] buttons = new Button[shopCosts.length];
        AlertDialog dialog = new AlertDialog.Builder(this)
            .setTitle("Магазин улучшений")
            .setView(list)
            .setNegativeButton("Закрыть", null)
            .create();

        for (int i = 0; i < buttons.length; i++) {
            final int item = i;
            buttons[i] = new Button(this);
            buttons[i].setAllCaps(false);
            buttons[i].setOnClickListener(v -> buyItem(item, dialog, buttons));
            list.addView(buttons[i]);
        }
        refreshShopButtons(buttons);
        dialog.show();
    }

    private void refreshShopButtons(Button[] buttons) {
        for (int i = 0; i < buttons.length; i++) {
            buttons[i].setText(shopNames[i] + "\n" + shopEffects[i] +
                "\nЦена: " + shopCosts[i] + " монет  |  Куплено: " + shopLevels[i]);
        }
    }

    private void buyItem(int item, AlertDialog dialog, Button[] buttons) {
        if (coins < shopCosts[item]) {
            Toast.makeText(this, "Недостаточно Земных Монет!", Toast.LENGTH_SHORT).show();
            return;
        }
        coins -= shopCosts[item];
        shopLevels[item]++;
        if (item == 0) {
            power++;
        } else if (item == 1) {
            power++;
            coinsPerSecond++;
        } else if (item == 2) {
            coinsPerSecond += 2;
        } else if (item == 3) {
            coinsPerSecond += 5;
        }
        shopCosts[item] = shopCosts[item] <= Integer.MAX_VALUE / 2
            ? shopCosts[item] * 2 : Integer.MAX_VALUE;
        saveGame();
        updateUI();
        if (buttons != null) refreshShopButtons(buttons);
        Toast.makeText(this, "Покупка: " + shopNames[item], Toast.LENGTH_SHORT).show();
    }

    private void updateUI() {
        coinsText.setText("Земные монеты: " + coins);
        clicksText.setText("Клики: " + clicks);
        powerText.setText("Сила клика: " + power);
        incomeText.setText("Доход: +" + coinsPerSecond + " монет/сек");
        upgradeButton.setText("УЛУЧШИТЬ КЛИК\nЦена: " + shopCosts[0] + " монет");
    }

    private void saveGame() {
        if (save == null) return;
        SharedPreferences.Editor editor = save.edit()
            .putInt("coins", coins)
            .putInt("clicks", clicks)
            .putInt("power", power)
            .putInt("coins_per_second", coinsPerSecond);
        for (int i = 0; i < shopCosts.length; i++) {
            editor.putInt("shop_cost_" + i, shopCosts[i]);
            editor.putInt("shop_level_" + i, shopLevels[i]);
        }
        editor.apply();
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(incomeTick);
        if (glView != null) glView.onPause();
        saveGame();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (glView != null) glView.onResume();
        handler.removeCallbacks(incomeTick);
        handler.postDelayed(incomeTick, 1000);
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
        private float aspect = 1f;

        private final String vertexShader =
            "attribute vec3 aPosition;" +
            "uniform float uAngle;" +
            "uniform float uTilt;" +
            "uniform float uAspect;" +
            "varying vec3 vNormal;" +
            "void main() {" +
            "float c = cos(uAngle);" +
            "float s = sin(uAngle);" +
            "vec3 p = vec3(aPosition.x*c-aPosition.z*s,aPosition.y,aPosition.x*s+aPosition.z*c);" +
            "float ct = cos(uTilt);" +
            "float st = sin(uTilt);" +
            "p = vec3(p.x,p.y*ct-p.z*st,p.y*st+p.z*ct);" +
            "vNormal = normalize(p);" +
            "gl_Position = vec4(p.x*0.54/uAspect,p.y*0.54,p.z*0.27,1.0);" +
            "gl_PointSize = 8.0;" +
            "}";

        private final String fragmentShader =
            "precision mediump float;" +
            "uniform int uPoint;" +
            "varying vec3 vNormal;" +
            "void main() {" +
            "if(uPoint == 1) {" +
            "vec2 q = gl_PointCoord-vec2(0.5);" +
            "if(dot(q,q)>0.25) discard;" +
            "gl_FragColor = vec4(0.12,0.9,0.2,1.0);" +
            "return;" +
            "}" +
            "vec3 n = normalize(vNormal);" +
            "float land = sin(n.x*13.0+sin(n.z*9.0))*sin(n.y*12.0+n.z*7.0);" +
            "vec3 ocean = vec3(0.015,0.12,0.48);" +
            "vec3 ground = vec3(0.12,0.48,0.16);" +
            "vec3 color = mix(ocean,ground,smoothstep(0.12,0.2,land));" +
            "vec3 light = normalize(vec3(-0.45,0.35,1.0));" +
            "float diffuse = max(dot(n,light),0.0);" +
            "float shade = 0.22 + diffuse*0.88;" +
            "color *= shade;" +
            "float rim = pow(1.0-max(dot(n,vec3(0.0,0.0,1.0)),0.0),3.0);" +
            "color += vec3(0.02,0.18,0.55)*rim*0.65;" +
            "gl_FragColor = vec4(color,1.0);" +
            "}";

        @Override
        public void onSurfaceCreated(javax.microedition.khronos.opengles.GL10 gl,
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
        public void onSurfaceChanged(javax.microedition.khronos.opengles.GL10 gl,
            int width, int height) {
            GLES20.glViewport(0, 0, width, height);
            aspect = height == 0 ? 1f : (float) width / (float) height;
        }

        @Override
        public void onDrawFrame(javax.microedition.khronos.opengles.GL10 gl) {
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);
            GLES20.glUseProgram(program);
            int pos = GLES20.glGetAttribLocation(program, "aPosition");
            int angleLoc = GLES20.glGetUniformLocation(program, "uAngle");
            int tiltLoc = GLES20.glGetUniformLocation(program, "uTilt");
            int aspectLoc = GLES20.glGetUniformLocation(program, "uAspect");
            int pointLoc = GLES20.glGetUniformLocation(program, "uPoint");
            GLES20.glUniform1f(angleLoc, angle);
            GLES20.glUniform1f(tiltLoc, tilt);
            GLES20.glUniform1f(aspectLoc, aspect);
            GLES20.glUniform1i(pointLoc, 0);
            GLES20.glEnableVertexAttribArray(pos);
            vertices.position(0);
            GLES20.glVertexAttribPointer(pos, 3, GLES20.GL_FLOAT, false, 12, vertices);
            GLES20.glDrawElements(GLES20.GL_TRIANGLES, indexCount, GLES20.GL_UNSIGNED_SHORT, indices);
            if (treeCount > 0 && treeBuffer != null) {
                GLES20.glUniform1i(pointLoc, 1);
                treeBuffer.position(0);
                GLES20.glVertexAttribPointer(pos, 3, GLES20.GL_FLOAT, false, 12, treeBuffer);
                GLES20.glDrawArrays(GLES20.GL_POINTS, 0, treeCount);
            }
            GLES20.glDisableVertexAttribArray(pos);
            angle += 0.008f;
        }

        void rotate(float x, float y) {
            angle += x * 0.02f;
            tilt += y * 0.02f;
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
            int lat = 48;
            int lon = 48;
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
