import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.RoundRectangle2D; // For rounded buttons
import java.util.ArrayList;
import java.util.Random;
import java.io.*;
import javax.sound.sampled.*;

@SuppressWarnings("unused")
public class FlappyBird extends JPanel implements ActionListener, KeyListener, MouseListener, MouseMotionListener {
    private static final int WIDTH = 800;
    private static final int HEIGHT = 600;
    private static final int GROUND_HEIGHT = 80; // Thicker ground
    private static final int BIRD_WIDTH = 44; // Slightly larger
    private static final int BIRD_HEIGHT = 34;
    private static final int PIPE_WIDTH = 85;
    private static final int PIPE_GAP = 200;
    private static final int PIPE_SPACING = 320; // More spacing
    private static final double GRAVITY = 0.55; // Snappier gravity
    private static final double JUMP_STRENGTH = -11;
    
    // Game State
    private Timer timer;
    private double birdY;
    private double birdVelocity;
    private ArrayList<Pipe> pipes;
    private Random random;
    private int score;
    private int highScore;
    private boolean gameOver;
    private boolean gameStarted;
    private boolean gamePaused;
    private boolean hardMode;
    private boolean nightMode;
    
    // Speed System
    private int speedLevel = 1; 
    private final int[] SPEED_THRESHOLDS = {10, 25}; 
    private final double[] BIRD_SPEEDS = {3.5, 4.5, 6.0}; 
    private final double[] JUMP_MODIFIERS = {1.0, 0.95, 0.9}; 
    
    // Animation
    private int birdAnimationFrame = 0;
    private int animationDelay = 0;
    private int backgroundOffset = 0;
    
    // Background
    @SuppressWarnings("unused")
    private int cloudOffset = 0;
    private ArrayList<Cloud> clouds;
    private ArrayList<Star> stars;
    private ArrayList<Tree> trees; // New Tree class for persistence
    
    // Sound
    private Clip jumpSound;
    private Clip scoreSound;
    private Clip hitSound;
    private Clip selectSound;
    private Clip speedUpSound;
    private boolean soundsEnabled = true;
    
    // High Score
    private static final String HIGH_SCORE_FILE = "flappybird_highscore.dat";
    
    // Buttons (Rectangles for hit detection)
    private Rectangle startBtnBound = new Rectangle(0, 0, 0, 0);
    private Rectangle hardModeBtnBound = new Rectangle(0, 0, 0, 0);
    private Rectangle nightModeBtnBound = new Rectangle(0, 0, 0, 0);
    private Rectangle soundBtnBound = new Rectangle(0, 0, 0, 0);
    
    private Rectangle restartBtnBound = new Rectangle(0, 0, 0, 0);
    private Rectangle menuBtnBound = new Rectangle(0, 0, 0, 0);
    private Rectangle resumeBtnBound = new Rectangle(0, 0, 0, 0); // For pause screen
    
    // Hover states
    private int hoveredButtonIndex = -1; // -1 none, 0 start, 1 hard, 2 night, 3 sound, 4 restart, 5 menu, 6 resume

    // Inner Classes
    private class Cloud {
        int x, y, width, height, speed;
        Cloud(int x, int y, int width, int height, int speed) {
            this.x = x; this.y = y; this.width = width; this.height = height; this.speed = speed;
        }
    }
    
    private class Star {
        int x, y, size;
        float brightness;
        Star(int x, int y, int size, float brightness) {
            this.x = x; this.y = y; this.size = size; this.brightness = brightness;
        }
    }

    private class Tree {
        int x, height, width, type;
        Tree(int x, int height, int width, int type) {
            this.x = x; this.height = height; this.width = width; this.type = type;
        }
    }

    public FlappyBird() {
        setPreferredSize(new Dimension(WIDTH, HEIGHT));
        setFocusable(true);
        addKeyListener(this);
        addMouseListener(this);
        addMouseMotionListener(this);
        
        timer = new Timer(16, this);
        random = new Random();
        
        clouds = new ArrayList<>();
        stars = new ArrayList<>();
        trees = new ArrayList<>();
        
        initializeClouds();
        initializeStars();
        initializeTrees();
        
        loadHighScore();
        loadSounds();
        resetGame();
    }
    
    private void initializeClouds() {
        clouds.clear();
        for (int i = 0; i < 6; i++) {
            clouds.add(new Cloud(random.nextInt(WIDTH * 2), random.nextInt(HEIGHT / 3), 
                                 80 + random.nextInt(60), 30 + random.nextInt(20), 1 + random.nextInt(2)));
        }
    }
    
    private void initializeStars() {
        stars.clear();
        if (nightMode) {
            for (int i = 0; i < 80; i++) {
                stars.add(new Star(random.nextInt(WIDTH), random.nextInt(HEIGHT / 2), 
                                   2 + random.nextInt(3), 0.5f + random.nextFloat() * 0.5f));
            }
        }
    }

    private void initializeTrees() {
        trees.clear();
        for (int i = 0; i < 15; i++) {
            int x = (i * (WIDTH / 10)) + random.nextInt(30);
            int h = 40 + random.nextInt(80); // Height
            int w = 20 + random.nextInt(15);
            int type = random.nextInt(3); // 0: Normal, 1: Round, 2: Pine
            trees.add(new Tree(x, h, w, type));
        }
    }
    
    private void loadHighScore() {
        try {
            File file = new File(HIGH_SCORE_FILE);
            if (file.exists()) {
                DataInputStream dis = new DataInputStream(new FileInputStream(file));
                highScore = dis.readInt();
                dis.close();
            }
        } catch (IOException e) { highScore = 0; }
    }
    
    private void saveHighScore() {
        try {
            DataOutputStream dos = new DataOutputStream(new FileOutputStream(HIGH_SCORE_FILE));
            dos.writeInt(highScore);
            dos.close();
        } catch (IOException e) { e.printStackTrace(); }
    }
    
    private void loadSounds() {
        try {
            jumpSound = loadSoundFromFile("jump.wav");
            scoreSound = loadSoundFromFile("score.wav");
            hitSound = loadSoundFromFile("hit.wav");
            selectSound = loadSoundFromFile("select.wav");
            speedUpSound = loadSoundFromFile("speedup.wav");
            
            // Fallbacks
            if (jumpSound == null) jumpSound = createTone(800, 100, 0.3f);
            if (scoreSound == null) scoreSound = createTone(1200, 150, 0.3f);
            if (hitSound == null) hitSound = createTone(200, 400, 0.5f);
            if (selectSound == null) selectSound = createTone(600, 100, 0.2f);
            if (speedUpSound == null) speedUpSound = createTone(1000, 200, 0.3f);
        } catch (Exception e) {
            soundsEnabled = false;
        }
    }
    
    private Clip loadSoundFromFile(String filename) {
        try {
            File soundFile = new File(filename);
            if (!soundFile.exists()) return null;
            AudioInputStream audioIn = AudioSystem.getAudioInputStream(soundFile);
            Clip clip = AudioSystem.getClip();
            clip.open(audioIn);
            return clip;
        } catch (Exception e) { return null; }
    }
    
    private Clip createTone(int frequency, int duration, float volume) throws LineUnavailableException {
        Clip clip = AudioSystem.getClip();
        AudioFormat format = new AudioFormat(44100, 8, 1, true, true);
        byte[] buffer = new byte[(int)(format.getFrameRate() * duration / 1000)];
        for (int i = 0; i < buffer.length; i++) {
            double angle = i / (format.getFrameRate() / frequency) * 2.0 * Math.PI;
            buffer[i] = (byte)(Math.sin(angle) * 127.0 * volume);
        }
        clip.open(format, buffer, 0, buffer.length);
        return clip;
    }
    
    private void playSound(Clip sound) {
        if (!soundsEnabled || sound == null) return;
        try {
            if (sound.isRunning()) sound.stop();
            sound.setFramePosition(0);
            sound.start();
        } catch (Exception e) {}
    }
    
    private void resetGame() {
        birdY = HEIGHT / 2;
        birdVelocity = 0;
        pipes = new ArrayList<>();
        score = 0;
        gameOver = false;
        gameStarted = false;
        gamePaused = false;
        birdAnimationFrame = 0;
        speedLevel = 1;
        
        initializeClouds();
        initializeStars(); // Resets night mode stars correctly
        
        for (int i = 0; i < 3; i++) {
            addPipe(WIDTH + 300 + i * PIPE_SPACING);
        }
    }
    
    private void addPipe(int x) {
        int minHeight = hardMode ? 80 : 120;
        int maxHeight = HEIGHT - PIPE_GAP - GROUND_HEIGHT - (hardMode ? 80 : 120);
        int pipeHeight = random.nextInt(maxHeight - minHeight) + minHeight;
        pipes.add(new Pipe(x, pipeHeight));
    }
    
    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2d = (Graphics2D) g;
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        
        drawBackground(g2d);
        
        for (Pipe pipe : pipes) {
            drawPipe(g2d, pipe);
        }
        
        drawGround(g2d);
        
        if (gameStarted && !gameOver) {
            drawBird(g2d);
        }
        
        drawHUD(g2d);
        
        if (!gameStarted) {
            drawStartScreen(g2d);
        } else if (gamePaused) {
            drawPauseScreen(g2d);
        } else if (gameOver) {
            drawGameOverScreen(g2d);
        }
    }
    
    private void drawBackground(Graphics2D g2d) {
        // Sky
        if (nightMode) {
            GradientPaint night = new GradientPaint(0, 0, new Color(20, 20, 70), 0, HEIGHT, new Color(10, 10, 30));
            g2d.setPaint(night);
            g2d.fillRect(0, 0, WIDTH, HEIGHT);
            
            // Stars
            g2d.setColor(Color.WHITE);
            for (Star s : stars) {
                g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, s.brightness));
                g2d.fillOval(s.x, s.y, s.size, s.size);
            }
            g2d.setComposite(AlphaComposite.SrcOver); // Reset
            
            // Moon
            g2d.setColor(new Color(240, 240, 220));
            g2d.fillOval(WIDTH - 150, 50, 80, 80);
        } else {
            GradientPaint day = new GradientPaint(0, 0, new Color(70, 180, 255), 0, HEIGHT, new Color(200, 240, 255));
            g2d.setPaint(day);
            g2d.fillRect(0, 0, WIDTH, HEIGHT);
            
            // Sun
            g2d.setColor(new Color(255, 230, 50));
            g2d.fillOval(WIDTH - 150, 50, 80, 80);
            g2d.setColor(new Color(255, 255, 100, 100)); // Glow
            g2d.fillOval(WIDTH - 160, 40, 100, 100);
            
            // Clouds
            for (Cloud c : clouds) {
                g2d.setColor(new Color(255, 255, 255, 220));
                g2d.fillOval(c.x, c.y, c.width, c.height);
                g2d.fillOval(c.x + c.width/3, c.y - c.height/2, c.width/2, c.height);
            }
        }
        
        // Cityscape Silhouette (Far background)
        g2d.setColor(nightMode ? new Color(10, 10, 30, 100) : new Color(150, 200, 255, 100));
        for (int i = 0; i < WIDTH; i+=60) {
           int h = 50 + (i % 70) + (i % 40);
           g2d.fillRect(i, HEIGHT - GROUND_HEIGHT - h, 60, h);
        }

        // Trees (Bottom anchored)
        for (Tree t : trees) {
            int treeX = (t.x + backgroundOffset) % (WIDTH + 50) - 50;
            if (treeX < -50) treeX += WIDTH + 50;
            drawTree(g2d, treeX, HEIGHT - GROUND_HEIGHT, t.width, t.height, t.type);
        }
    }

    private void drawTree(Graphics2D g2d, int x, int groundY, int w, int h, int type) {
        // Trunk
        g2d.setColor(new Color(100, 60, 20));
        g2d.fillRect(x + w/2 - 4, groundY - h, 8, h);
        
        // Leaves
        g2d.setColor(nightMode ? new Color(20, 80, 30) : new Color(40, 180, 60));
        if (type == 0) { // Triangle (Pine-ish)
             g2d.fillPolygon(new int[]{x, x+w, x+w/2}, new int[]{groundY - h/3, groundY - h/3, groundY - h - 20}, 3);
             g2d.fillPolygon(new int[]{x-5, x+w+5, x+w/2}, new int[]{groundY - h/2, groundY - h/2, groundY - h - 10}, 3);
        } else if (type == 1) { // Round
            g2d.fillOval(x - 5, groundY - h - 15, w + 10, w + 10);
            g2d.fillOval(x - 10, groundY - h - 5, w + 20, w + 10);
        } else { // Bushy
            g2d.fillOval(x, groundY - h - 10, w, w);
            g2d.fillOval(x - 8, groundY - h, w, w);
            g2d.fillOval(x + 8, groundY - h - 5, w, w);
        }
    }
    
    private void drawGround(Graphics2D g2d) {
        // Top grass strip
        g2d.setColor(new Color(60, 180, 40));
        g2d.fillRect(0, HEIGHT - GROUND_HEIGHT, WIDTH, 15);
        g2d.setColor(new Color(100, 220, 60)); // Highlight
        g2d.fillRect(0, HEIGHT - GROUND_HEIGHT, WIDTH, 5);
        
        // Dirt body
        GradientPaint dirt = new GradientPaint(0, HEIGHT - GROUND_HEIGHT + 15, new Color(210, 180, 140), 
                                               0, HEIGHT, new Color(160, 120, 80));
        g2d.setPaint(dirt);
        g2d.fillRect(0, HEIGHT - GROUND_HEIGHT + 15, WIDTH, GROUND_HEIGHT - 15);
        
        // Dirt details
        g2d.setColor(new Color(180, 150, 110));
        for (int i = 0; i < WIDTH; i += 20) {
             if (i % 3 == 0) g2d.fillRect((i + backgroundOffset) % WIDTH, HEIGHT - GROUND_HEIGHT + 25, 4, 4);
             if (i % 4 == 0) g2d.fillRect((i + backgroundOffset + 10) % WIDTH, HEIGHT - GROUND_HEIGHT + 45, 6, 4);
        }
    }
    
    private void drawPipe(Graphics2D g2d, Pipe pipe) {
        Color mainColor, darkColor, lightColor;
        
        // Childish/Professional colors (Vibrant but clean)
        if (speedLevel == 3) { mainColor = new Color(220, 60, 60); } // Red
        else if (speedLevel == 2) { mainColor = new Color(240, 160, 40); } // Orange
        else { mainColor = new Color(80, 200, 60); } // Green
        
        darkColor = mainColor.darker();
        lightColor = mainColor.brighter();
        
        // Top Pipe
        g2d.setPaint(new GradientPaint(pipe.x, 0, lightColor, pipe.x + PIPE_WIDTH, 0, darkColor));
        g2d.fillRoundRect(pipe.x, -50, PIPE_WIDTH, pipe.height + 50, 10, 10); // Rounded bottom
        // Cap
        g2d.setColor(darkColor);
        g2d.fillRoundRect(pipe.x - 4, pipe.height - 25, PIPE_WIDTH + 8, 25, 5, 5);
        g2d.setColor(lightColor);
        g2d.fillRect(pipe.x - 2, pipe.height - 23, PIPE_WIDTH + 4, 3);
        
        // Bottom Pipe
        int bottomY = pipe.height + (hardMode ? PIPE_GAP - 40 : PIPE_GAP);
        g2d.setPaint(new GradientPaint(pipe.x, 0, lightColor, pipe.x + PIPE_WIDTH, 0, darkColor));
        g2d.fillRoundRect(pipe.x, bottomY, PIPE_WIDTH, HEIGHT - bottomY - GROUND_HEIGHT + 50, 10, 10);
        // Cap
        g2d.setColor(darkColor);
        g2d.fillRoundRect(pipe.x - 4, bottomY, PIPE_WIDTH + 8, 25, 5, 5);
        g2d.setColor(lightColor);
        g2d.fillRect(pipe.x - 2, bottomY + 2, PIPE_WIDTH + 4, 3);
    }
    
    private void drawBird(Graphics2D g2d) {
        int birdX = WIDTH / 4 - BIRD_WIDTH / 2;
        int birdYDraw = (int)birdY - BIRD_HEIGHT / 2;
        
        AffineTransform old = g2d.getTransform();
        g2d.rotate(Math.toRadians(Math.min(30, Math.max(-90, birdVelocity * 4))), birdX + BIRD_WIDTH/2, birdY);
        
        // Body
        g2d.setColor(Color.YELLOW);
        if (speedLevel == 2) g2d.setColor(new Color(255, 200, 50));
        if (speedLevel == 3) g2d.setColor(new Color(255, 100, 50));
        
        g2d.fillOval(birdX, birdYDraw, BIRD_WIDTH, BIRD_HEIGHT);
        
        // White belly
        g2d.setColor(new Color(255, 255, 220));
        g2d.fillOval(birdX + 5, birdYDraw + BIRD_HEIGHT/2, BIRD_WIDTH - 15, BIRD_HEIGHT/2 - 2);
        
        // Eye (Big & Cute)
        g2d.setColor(Color.WHITE);
        g2d.fillOval(birdX + BIRD_WIDTH - 15, birdYDraw + 2, 14, 14);
        g2d.setColor(Color.BLACK);
        g2d.fillOval(birdX + BIRD_WIDTH - 9, birdYDraw + 6, 6, 6);
        
        // Beak
        g2d.setColor(new Color(255, 100, 0));
        g2d.fillPolygon(new int[]{birdX+BIRD_WIDTH-5, birdX+BIRD_WIDTH+8, birdX+BIRD_WIDTH-5},
                        new int[]{birdYDraw+15, birdYDraw+20, birdYDraw+25}, 3);
                        
        // Wing (Flapping)
        g2d.setColor(new Color(240, 240, 240));
        int wingY = birdYDraw + 15;
        if (birdAnimationFrame == 1) wingY -= 8;
        if (birdAnimationFrame == 2) wingY += 5;
        g2d.fillOval(birdX - 2, wingY, 22, 14);
        g2d.setColor(Color.BLACK);
        g2d.setStroke(new BasicStroke(1));
        g2d.drawOval(birdX - 2, wingY, 22, 14);
        
        // Outline
        g2d.setStroke(new BasicStroke(2));
        g2d.drawOval(birdX, birdYDraw, BIRD_WIDTH, BIRD_HEIGHT);
        
        g2d.setTransform(old);
    }
    
    private void drawHUD(Graphics2D g2d) {
        // Score
        String scoreTxt = String.valueOf(score);
        g2d.setFont(new Font("Comic Sans MS", Font.BOLD, 60));
        
        // Stroke
        g2d.setColor(Color.BLACK);
        g2d.drawString(scoreTxt, WIDTH/2 - 32, 82);
        g2d.drawString(scoreTxt, WIDTH/2 - 28, 82);
        g2d.drawString(scoreTxt, WIDTH/2 - 32, 78);
        g2d.drawString(scoreTxt, WIDTH/2 - 28, 78);
        
        g2d.setColor(Color.WHITE);
        g2d.drawString(scoreTxt, WIDTH/2 - 30, 80);
    }
    
    // ==================================================================================
    // UI SCREENS WITH BUTTONS
    // ==================================================================================
    
    private void drawButton(Graphics2D g2d, Rectangle rect, String text, Color baseColor, boolean isHovered) {
        Color c = isHovered ? baseColor.brighter() : baseColor;
        
        // Shadow
        g2d.setColor(c.darker().darker());
        g2d.fillRoundRect(rect.x, rect.y + 4, rect.width, rect.height, 20, 20);
        
        // Body
        g2d.setColor(c);
        g2d.fillRoundRect(rect.x, rect.y, rect.width, rect.height, 20, 20);
        
        // Text
        g2d.setColor(Color.WHITE);
        g2d.setFont(new Font("Arial Rounded MT Bold", Font.BOLD, 22));
        FontMetrics fm = g2d.getFontMetrics();
        int tx = rect.x + (rect.width - fm.stringWidth(text))/2;
        int ty = rect.y + (rect.height + fm.getAscent())/2 - 5;
        g2d.drawString(text, tx, ty);
        
        // Border
        g2d.setColor(Color.WHITE);
        g2d.setStroke(new BasicStroke(3));
        g2d.drawRoundRect(rect.x + 2, rect.y + 2, rect.width - 4, rect.height - 4, 16, 16);
    }

    private void drawStartScreen(Graphics2D g2d) {
        // Overlay
        g2d.setColor(new Color(0, 0, 0, 100));
        g2d.fillRect(0, 0, WIDTH, HEIGHT);
        
        // Title
        drawTitle(g2d, "FLAPPY BIRD", 150);
        
        // Setup Buttons
        int btnW = 200;
        int btnH = 50;
        int centerX = WIDTH/2 - btnW/2;
        
        startBtnBound = new Rectangle(centerX, 250, btnW, btnH);
        hardModeBtnBound = new Rectangle(centerX, 320, btnW, btnH);
        nightModeBtnBound = new Rectangle(centerX, 390, btnW, btnH);
        soundBtnBound = new Rectangle(centerX, 460, btnW, btnH);
        
        drawButton(g2d, startBtnBound, "PLAY GAME", new Color(80, 200, 60), hoveredButtonIndex == 0);
        drawButton(g2d, hardModeBtnBound, hardMode ? "HARD: ON" : "HARD: OFF", new Color(220, 60, 60), hoveredButtonIndex == 1);
        drawButton(g2d, nightModeBtnBound, nightMode ? "NIGHT: ON" : "NIGHT: OFF", new Color(60, 60, 180), hoveredButtonIndex == 2);
        drawButton(g2d, soundBtnBound, soundsEnabled ? "SOUND: ON" : "SOUND: OFF", new Color(220, 180, 40), hoveredButtonIndex == 3);
        
        // Hint
        g2d.setColor(Color.WHITE);
        g2d.setFont(new Font("Arial", Font.ITALIC, 16));
        g2d.drawString("Press Space or Click to Jump!", WIDTH/2 - 100, 560);
    }
    
    private void drawGameOverScreen(Graphics2D g2d) {
        g2d.setColor(new Color(0, 0, 0, 150));
        g2d.fillRect(0, 0, WIDTH, HEIGHT);
        
        drawTitle(g2d, "GAME OVER", 180);
        
        // Score Panel
        g2d.setColor(new Color(230, 230, 230));
        g2d.fillRoundRect(WIDTH/2 - 120, 220, 240, 120, 20, 20);
        g2d.setColor(Color.BLACK);
        g2d.drawRoundRect(WIDTH/2 - 120, 220, 240, 120, 20, 20);
        
        g2d.setFont(new Font("Arial", Font.PLAIN, 20));
        g2d.drawString("Score", WIDTH/2 - 100, 260);
        g2d.drawString("Best", WIDTH/2 - 100, 310);
        
        g2d.setFont(new Font("Arial", Font.BOLD, 30));
        g2d.drawString(String.valueOf(score), WIDTH/2 + 50, 260);
        g2d.drawString(String.valueOf(highScore), WIDTH/2 + 50, 310);
        
        // Buttons
        int btnW = 160;
        int centerX = WIDTH/2 - btnW/2;
        restartBtnBound = new Rectangle(centerX, 380, btnW, 50);
        menuBtnBound = new Rectangle(centerX, 450, btnW, 50);
        
        drawButton(g2d, restartBtnBound, "RETRY", new Color(80, 200, 60), hoveredButtonIndex == 4);
        drawButton(g2d, menuBtnBound, "MENU", new Color(60, 160, 220), hoveredButtonIndex == 5);
    }
    
    private void drawPauseScreen(Graphics2D g2d) {
        g2d.setColor(new Color(0, 0, 0, 100));
        g2d.fillRect(0, 0, WIDTH, HEIGHT);
        drawTitle(g2d, "PAUSED", 250);
        
        resumeBtnBound = new Rectangle(WIDTH/2 - 80, 300, 160, 50);
        drawButton(g2d, resumeBtnBound, "RESUME", new Color(220, 180, 40), hoveredButtonIndex == 6);
    }
    
    private void drawTitle(Graphics2D g2d, String text, int y) {
        g2d.setFont(new Font("Comic Sans MS", Font.BOLD, 55));
        FontMetrics fm = g2d.getFontMetrics();
        int x = (WIDTH - fm.stringWidth(text)) / 2;
        
        // Shadow
        g2d.setColor(Color.BLACK);
        g2d.drawString(text, x+4, y+4);
        // Main
        g2d.setColor(Color.WHITE);
        g2d.drawString(text, x, y);
        g2d.setColor(new Color(255, 120, 0)); // Orange center
        g2d.drawString(text, x+2, y-2);
    }

    // ==================================================================================
    // LOGIC
    // ==================================================================================

    @Override
    public void actionPerformed(ActionEvent e) {
        if (!gameStarted || gameOver || gamePaused) return;

        animationDelay++;
        if (animationDelay >= 5) {
            birdAnimationFrame = (birdAnimationFrame + 1) % 3;
            animationDelay = 0;
        }
        
        // Background Scroll
        Double speedMod = 1.0;
        if(speedLevel == 2) speedMod = 1.2;
        if(speedLevel == 3) speedMod = 1.5;
        
        backgroundOffset += (1 * speedMod); // Slow parallax
        
        // Clouds
        for (Cloud c : clouds) {
            c.x -= c.speed * speedMod;
            if (c.x + c.width < 0) { c.x = WIDTH; c.y = random.nextInt(HEIGHT/3); }
        }
        
        // Logic
        birdVelocity += GRAVITY * (hardMode ? 1.2 : 1.0);
        birdY += birdVelocity;
        
        // Pipes
        for (int i = 0; i < pipes.size(); i++) {
            Pipe pipe = pipes.get(i);
            pipe.x -= BIRD_SPEEDS[speedLevel - 1] * (hardMode ? 1.3 : 1.0);
            
            if (!pipe.passed && pipe.x + PIPE_WIDTH < WIDTH / 4) {
                pipe.passed = true;
                score++;
                if (score > highScore) { highScore = score; saveHighScore(); }
                playSound(scoreSound);
                
                // Speed check
                if (score == SPEED_THRESHOLDS[0] || score == SPEED_THRESHOLDS[1]) playSound(speedUpSound);
                if (score >= SPEED_THRESHOLDS[1]) speedLevel = 3;
                else if (score >= SPEED_THRESHOLDS[0]) speedLevel = 2;
            }
            
            if (pipe.x + PIPE_WIDTH < 0) {
                pipes.remove(i);
                addPipe(pipes.get(pipes.size() - 1).x + PIPE_SPACING);
                i--;
            }
            
            if (checkCollision(pipe)) {
               triggerGameOver();
               return; // STOP IMMEDIATELY
            }
        }
        
        if (birdY > HEIGHT - GROUND_HEIGHT - BIRD_HEIGHT/2 || birdY < 0) {
            triggerGameOver();
        }
        
        repaint();
    }
    
    private void triggerGameOver() {
        gameOver = true;
        playSound(hitSound);
        timer.stop();
        repaint();
    }
    
    private boolean checkCollision(Pipe pipe) {
        // Precise Hitbox
        Rectangle birdRect = new Rectangle(WIDTH/4 - BIRD_WIDTH/2 + 5, (int)birdY - BIRD_HEIGHT/2 + 5, BIRD_WIDTH - 10, BIRD_HEIGHT - 10);
        
        int gap = hardMode ? PIPE_GAP - 40 : PIPE_GAP;
        
        Rectangle topPipeVal = new Rectangle(pipe.x, -100, PIPE_WIDTH, pipe.height + 100);
        Rectangle botPipeVal = new Rectangle(pipe.x, pipe.height + gap, PIPE_WIDTH, 1000);
        
        return birdRect.intersects(topPipeVal) || birdRect.intersects(botPipeVal);
    }
    
    @Override
    public void mouseClicked(MouseEvent e) {
        int mx = e.getX();
        int my = e.getY();
        
        if (!gameStarted) {
            if (startBtnBound.contains(mx, my)) {
                startGame();
            } else if (hardModeBtnBound.contains(mx, my)) {
                hardMode = !hardMode; playSound(selectSound); repaint();
            } else if (nightModeBtnBound.contains(mx, my)) {
                nightMode = !nightMode; initializeStars(); playSound(selectSound); repaint();
            } else if (soundBtnBound.contains(mx, my)) {
                soundsEnabled = !soundsEnabled; playSound(selectSound); repaint();
            }
        } else if (gameOver) {
            if (restartBtnBound.contains(mx, my)) {
                resetGame(); startGame();
            } else if (menuBtnBound.contains(mx, my)) {
                resetGame(); repaint();
            }
        } else if (gamePaused) {
            if (resumeBtnBound.contains(mx, my)) {
                gamePaused = false; repaint();
            }
        } else {
            // Game playing - Click to jump
            jump();
        }
    }
    
    @Override
    public void mousePressed(MouseEvent e) {}
    @Override
    public void mouseReleased(MouseEvent e) {}
    @Override
    public void mouseEntered(MouseEvent e) {}
    @Override
    public void mouseExited(MouseEvent e) {}
    
    @Override
    public void mouseMoved(MouseEvent e) {
        int mx = e.getX();
        int my = e.getY();
        int oldHover = hoveredButtonIndex;
        hoveredButtonIndex = -1;
        
        if (!gameStarted) {
            if (startBtnBound.contains(mx, my)) hoveredButtonIndex = 0;
            else if (hardModeBtnBound.contains(mx, my)) hoveredButtonIndex = 1;
            else if (nightModeBtnBound.contains(mx, my)) hoveredButtonIndex = 2;
            else if (soundBtnBound.contains(mx, my)) hoveredButtonIndex = 3;
        } else if (gameOver) {
            if (restartBtnBound.contains(mx, my)) hoveredButtonIndex = 4;
            else if (menuBtnBound.contains(mx, my)) hoveredButtonIndex = 5;
        } else if (gamePaused) {
            if (resumeBtnBound.contains(mx, my)) hoveredButtonIndex = 6;
        }
        
        if (hoveredButtonIndex != -1) setCursor(new Cursor(Cursor.HAND_CURSOR));
        else setCursor(new Cursor(Cursor.DEFAULT_CURSOR));
        
        if (oldHover != hoveredButtonIndex) repaint();
    }
    
    @Override
    public void mouseDragged(MouseEvent e) {}

    @Override
    public void keyPressed(KeyEvent e) {
        if (e.getKeyCode() == KeyEvent.VK_SPACE) {
            if (!gameStarted && !gameOver) startGame();
            else if (gameStarted && !gamePaused && !gameOver) jump();
        }
        if (e.getKeyCode() == KeyEvent.VK_P && gameStarted && !gameOver) {
            gamePaused = !gamePaused; repaint();
        }
    }

    @Override
    public void keyReleased(KeyEvent e) {}
    @Override
    public void keyTyped(KeyEvent e) {}
    
    private void startGame() {
        gameStarted = true;
        timer.start();
        playSound(selectSound);
        jump();
    }
    
    private void jump() {
        birdVelocity = JUMP_STRENGTH * JUMP_MODIFIERS[speedLevel - 1];
        playSound(jumpSound);
    }

    private class Pipe {
        int x, height;
        boolean passed;
        Pipe(int x, int h) { this.x = x; this.height = h; passed = false; }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("Flappy Bird: Kids Edition");
            FlappyBird game = new FlappyBird();
            frame.add(game);
            frame.pack();
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setLocationRelativeTo(null);
            frame.setResizable(false);
            frame.setVisible(true);
        });
    }
}
