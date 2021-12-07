package com.example.jpct_demo

import android.R
import android.app.Activity
import android.opengl.GLSurfaceView
import android.os.*
import android.view.MotionEvent
import com.threed.jpct.*
import com.threed.jpct.util.BitmapHelper
import com.threed.jpct.util.MemoryHelper
import java.util.*
import javax.microedition.khronos.egl.EGL10
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/**
 *
 * 一个简单的例子。比起展示如何写一个正确的android应用它更着重于展示如何使用JPCT-AE这个3D游戏框架。
 * 它包含了Activity类去处理pause和resume等方法
 */
class MainActivity : Activity() {
    // GLSurfaceView对象
    private var mGLView: GLSurfaceView? = null

    // 类MyRenderer对象
    private var renderer: MyRenderer? = null

    // 当JPCT渲染背景时FrameBuffer类提供了一个缓冲,它的结果本质上是一个能显示或者修改甚至能进行更多后处理的图片。
    private var fb: FrameBuffer? = null

    // World类是JPCT时最重要的一个类，它好像胶水一样把事物"粘"起来。它包含的对象和光线定义了JPCT的场景
    private var world: World? = null

    // 类似java.awt.*中的Color类
    private val back = RGBColor(50, 50, 100)
    private var touchTurn = 0f
    private var touchTurnUp = 0f
    private var xpos = -1f
    private var ypos = -1f

    /*
	 * Object3D类是一个三维对象,千万不要傻呼呼的认为它与java.lang.Object类似。
	 * 一个Object3D对象作为一个实例被添加到在渲染的World对象中。Object3D在World中一次添加一个实例
	 * ，他们可能被联系起作为孩子/父母来在他们中建立一个制度.
	 * 人体模型当然也能应用在以上的规则中。他们常常不加到一个World实例中，而是绑定到其它对象中(人体模型或非人体模型)。有些方法
	 * 在这个类中需要一个实例添加到一个World实例中(用World.addObject()方法可以实现)。
	 */
    private var cube: Object3D? = null

    // 每秒帧数
    private var fps = 0

    // 光照类
    private var sun: Light? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        // Logger类中 jPCT中一个普通的用于打印和存储消息，错误和警告的日志类。 每一个JPCT生成的消息将被加入到这个类的队列中
        Logger.log("onCreate")
        // 如果本类对象不为NULL,将从Object中所有属性装入该类
        if (master != null) {
            copy(master)
        }
        super.onCreate(savedInstanceState)
        // 实例化GLSurfaceView
        mGLView = GLSurfaceView(this)
        // 使用自己实现的 EGLConfigChooser,该实现必须在setRenderer(renderer)之前
        // 如果没有setEGLConfigChooser方法被调用，则默认情况下，视图将选择一个与当前android.view.Surface兼容至少16位深度缓冲深度EGLConfig。
        mGLView!!.setEGLConfigChooser { egl, display -> // Ensure that we get a 16bit framebuffer. Otherwise, we'll fall
            // back to Pixelflinger on some device (read: Samsung I7500)
            val attributes = intArrayOf(
                EGL10.EGL_DEPTH_SIZE, 16,
                EGL10.EGL_NONE
            )
            val configs = arrayOfNulls<EGLConfig>(1)
            val result = IntArray(1)
            egl.eglChooseConfig(display, attributes, configs, 1, result)
            configs[0]!!
        }
        // 实例化MyRenderer
        renderer = MyRenderer()
        // 设置View的渲染器，同时启动线程调用渲染，以至启动渲染
        mGLView!!.setRenderer(renderer)
        // 设置一个明确的视图
        setContentView(mGLView)
    }

    // 重写onPause()
    override fun onPause() {
        super.onPause()
        mGLView!!.onPause()
    }

    // 重写onResume()
    override fun onResume() {
        super.onResume()
        mGLView!!.onResume()
    }

    // 重写onStop()
    override fun onStop() {
        super.onStop()
    }

    private fun copy(src: Any?) {
        try {
            // 打印日志
            Logger.log("Copying data from master Activity!")
            // 返回一个数组，其中包含目前这个类的的所有字段的Filed对象
            val fs = src!!.javaClass.declaredFields
            // 遍历fs数组
            for (f in fs) {
                // 尝试设置无障碍标志的值。标志设置为false将使访问检查，设置为true，将其禁用。
                f.isAccessible = true
                // 将取到的值全部装入当前类中
                f[this] = f[src]
            }
        } catch (e: java.lang.Exception) {
            // 抛出运行时异常
            throw java.lang.RuntimeException(e)
        }
    }

    override fun onTouchEvent(me: MotionEvent): Boolean {
        // 按键开始
        if (me.action == MotionEvent.ACTION_DOWN) {
            // 保存按下的初始x,y位置于xpos,ypos中
            xpos = me.x
            ypos = me.y
            return true
        }
        // 按键结束
        if (me.action == MotionEvent.ACTION_UP) {
            // 设置x,y及旋转角度为初始值
            xpos = -1f
            ypos = -1f
            touchTurn = 0f
            touchTurnUp = 0f
            return true
        }
        if (me.action == MotionEvent.ACTION_MOVE) {
            // 计算x,y偏移位置及x,y轴上的旋转角度
            val xd = me.x - xpos
            val yd = me.y - ypos
            // Logger.log("me.getX() - xpos----------->>"
            // + (me.getX() - xpos));
            xpos = me.x
            ypos = me.y
            Logger.log("xpos------------>>$xpos")
            // Logger.log("ypos------------>>" + ypos);
            // 以x轴为例，鼠标从左向右拉为正，从右向左拉为负
            touchTurn = xd / -100f
            touchTurnUp = yd / -100f
            Logger.log("touchTurn------------>>$touchTurn")
            // Logger.log("touchTurnUp------------>>" + touchTurnUp);
            return true
        }
        // 每Move一下休眠毫秒
        try {
            Thread.sleep(15)
        } catch (e: java.lang.Exception) {
            // No need for this...
        }
        return super.onTouchEvent(me)
    }

    // MyRenderer类实现GLSurfaceView.Renderer接口
    internal inner class MyRenderer : GLSurfaceView.Renderer {
        // 当前系统的毫秒数
        private var time = System.currentTimeMillis()

        // 是否停止
        private var stop = false

        // 停止
        fun stop() {
            stop = true
        }

        // 当屏幕改变时
        override fun onSurfaceChanged(gl: GL10, w: Int, h: Int) {
            // 如果FrameBuffer不为NULL,释放fb所占资源
            if (fb != null) {
                fb!!.dispose()
            }
            // 创建一个宽度为w,高为h的FrameBuffer
            fb = FrameBuffer(gl, w, h)
            Logger.log(master.toString() + "")
            // 如果master为空
            if (master == null) {
                // 实例化World对象
                world = World()
                // 设置了环境光源强度。设置此值是负的整个场景会变暗，而为正将照亮了一切。
                world!!.setAmbientLight(20, 20, 20)
                // 在World中创建一个新的光源
                sun = Light(world)
                // 设置光照强度
                sun!!.setIntensity(250f, 250f, 250f)
                // 创建一个纹理
                // 构造方法Texture(Bitmap image)
                // static Bitmap rescale(Bitmap bitmap, int width, int height)
                // static Bitmap convert(Drawable drawable)
                val texture = Texture(
                    BitmapHelper.rescale(
                        BitmapHelper.convert(
                            resources.getDrawable(
                                R.drawable.ic_menu_compass
                            )
                        ), 64, 64
                    )
                )
                // TextureManager.getInstance()取得一个Texturemanager对象
                // addTexture("texture",texture)添加一个纹理
                TextureManager.getInstance().addTexture("texture", texture)
                // Object3D对象开始了:-)
                // Primitives提供了一些基本的三维物体，假如你为了测试而生成一些对象或为
                // 其它目的使用这些类将很明智，因为它即快速又简单，不需要载入和编辑。
                // 调用public static Object3D getCube(float scale) scale:角度
                // 返回一个立方体
                cube = Primitives.getCube(10f)
                // 以纹理的方式给对象所有面"包装"上纹理
                cube!!.calcTextureWrapSpherical()
                // 给对象设置纹理
                cube!!.setTexture("texture")
                // 除非你想在事后再用PolygonManager修改,否则释放那些不再需要数据的内存
                cube!!.strip()
                // 初始化一些基本的对象是几乎所有进一步处理所需的过程。
                // 如果对象是"准备渲染"(装载，纹理分配，安置，渲染模式设置，
                // 动画和顶点控制器分配),那么build()必须被调用，
                cube!!.build()
                // 将Object3D对象添加到world集合
                world!!.addObject(cube)
                // 该Camera代表了Camera/viewer在当前场景的位置和方向，它也包含了当前视野的有关信息
                // 你应该记住Camera的旋转矩阵实际上是应用在World中的对象的一个旋转矩阵。
                // 这一点很重要，当选择了Camera的旋转角度，一个Camera(虚拟)围绕w旋转和通过围绕World围绕w旋转、
                // 将起到相同的效果，因此，考虑到旋转角度，World围绕camera时，camera的视角是静态的。假如你不喜欢
                // 这种习惯，你可以使用rotateCamera()方法
                val cam = world!!.camera
                // 以50有速度向后移动Camera（相对于目前的方向）
                cam.moveCamera(Camera.CAMERA_MOVEOUT, 50f)
                // cub.getTransformedCenter()返回对象的中心
                // cam.lookAt(SimpleVector lookAt))
                // 旋转这样camera以至于它看起来是在给定的world-space 的位置
                cam.lookAt(cube!!.getTransformedCenter())
                // SimpleVector是一个代表三维矢量的基础类，几乎每一个矢量都
                // 是用SimpleVector或者至少是一个SimpleVector变体构成的(有时由于
                // 某些原因比如性能可能会用(float x,float y,float z)之类)。
                val sv = SimpleVector()
                // 将当前SimpleVector的x,y,z值设为给定的SimpleVector(cube.getTransformedCenter())的值
                sv.set(cube!!.getTransformedCenter())
                // Y方向上减去100
                sv.y -= 100f
                // Z方向上减去100
                sv.z -= 100f
                // 设置光源位置
                sun!!.position = sv
                // 强制GC和finalization工作来试图去释放一些内存，同时将当时的内存写入日志，
                // 这样可以避免动画不连贯的情况，然而，它仅仅是减少这种情况发生的机率
                MemoryHelper.compact()
                // 如果master为空,使用日志记录且设master为HelloWorld本身
                if (master == null) {
                    Logger.log("Saving master Activity!")
                    master = this@MainActivity
                }
            }
        }

        // 需实现的onSurfaceCreated(GL10 gl, EGLConfig config)
        override fun onSurfaceCreated(gl: GL10, config: EGLConfig) {}

        // 绘制到当前屏幕哦:-D
        override fun onDrawFrame(gl: GL10) {
            try {
                // 如果stop为true
                if (!stop) {
                    // 如果touchTurn不为0,向Y轴旋转touchTure角度
                    if (touchTurn != 0f) {
                        // 旋转物体的旋转绕Y由给定矩阵W轴角（弧度顺时针方向为正值）,应用到对象下一次渲染时。
                        cube!!.rotateY(touchTurn)
                        // 将touchTurn置0
                        touchTurn = 0f
                    }
                    if (touchTurnUp != 0f) {
                        // 旋转物体的旋转围绕x由给定角度宽（弧度，逆时针为正值）轴矩阵,应用到对象下一次渲染时。
                        cube!!.rotateX(touchTurnUp)
                        // 将touchTureUp置0
                        touchTurnUp = 0f
                    }
                    // 用给定的颜色(back)清除FrameBuffer
                    fb!!.clear(back)
                    // 变换和灯光所有多边形
                    world!!.renderScene(fb)
                    // 绘制
                    world!!.draw(fb)
                    // 渲染图像显示
                    fb!!.display()
                    // 记录FPS
                    if (System.currentTimeMillis() - time >= 1000) {
                        // Logger.log(fps + "fps");
                        fps = 0
                        time = System.currentTimeMillis()
                    }
                    fps++
                    // 如果stop为false,释放FrameBuffer
                } else {
                    if (fb != null) {
                        fb!!.dispose()
                        fb = null
                    }
                }
                // 当出现异常，打印异常信息
            } catch (e: java.lang.Exception) {
                Logger.log(e, Logger.MESSAGE)
            }
        }
    }

    companion object {
        // HelloWorld对象用来处理Activity的onPause和onResume方法
        private var master: MainActivity? = null
    }
}