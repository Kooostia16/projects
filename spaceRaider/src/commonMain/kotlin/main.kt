import com.soywiz.klock.seconds
import com.soywiz.klock.hr.*
import com.soywiz.korge.*
import com.soywiz.korim.bitmap.*
import com.soywiz.korim.color.*
import com.soywiz.korge.input.*
import com.soywiz.korge.component.*
import com.soywiz.korge.view.*
import com.soywiz.korim.color.Colors
import com.soywiz.korim.format.*
import com.soywiz.korma.geom.vector.*
import com.soywiz.korma.geom.shape.*
import com.soywiz.korma.geom.*
import com.soywiz.korma.math.*
import com.soywiz.korev.*
import kotlin.math.*
import kotlin.random.*
import com.soywiz.korio.file.std.*

val planetColors: Array<RGBA> = arrayOf(Colors.GREEN, Colors.AQUA, Colors.RED, Colors.YELLOW, Colors.BLUE)
val PI_HALF: Angle = (3.14/2.0).radians
val PI_HALFD: Double = (3.14/2.0)

suspend fun main() = Korge(width = 512, height = 512, bgcolor = Colors["#cac5e5"]) {
	val baseScale: Double = scaleX // Stage.scaleX might be different to 1.0 on retina displays
	val player = image(resourcesVfs["/assets/spaceship.png"].readBitmap()) {
		x = 40.0
		y = 40.0
		scaleX = 2.0
		scaleY = 2.0
		anchorX = 0.5
		anchorY = 0.6
		smoothing = false
	}

	player.xy(100, 100)

	var rot = Point(0.0, 0.1)
	var vecForce = Point(0.0, 0.0)
	var addForce = Point(0.0, 0.0)
	var addForceMult = 0.0
	var maxForce = 20.0

	val wo = PlanetGenerator(this, player)

	wo.generate(10)

	fun restart() {
		scale(baseScale * 0.8, baseScale * 0.8)
		wo.regenerate(Random.nextInt(50, 60))
		player.apply {
			x = 256.0
			y = 450.0
		}
		vecForce = Point(0, 0)
		addForce = Point(0, 0)
	}

	addHrUpdater() { t ->
		val dt = t / 16.hrMilliseconds
		addForce.x = addForceMult * cos(player.rotation - PI_HALF) * maxForce
		addForce.y = addForceMult * sin(player.rotation - PI_HALF) * maxForce
		var gravVec = Point(256 - player.x, 256 - player.y)
		var gravAngle = atan2(gravVec.y, gravVec.x)
		//vecForce.x += cos(gravAngle)*5*dt*0.001
		//vecForce.y += sin(gravAngle)*5*dt*0.001
		var vec = Point(addForce.x - vecForce.x, addForce.y - vecForce.y)
		vecForce.x += vec.x * dt * 0.001
		vecForce.y += vec.y * dt * 0.001
		player.x += vecForce.x
		player.y += vecForce.y
		player.rotation += (rot.x * rot.y * dt).radians
		scaleX = baseScale * ((scaleX + dt * 0.01).clamp(0.4, 1.0))
		scaleY = baseScale * ((scaleY + dt * 0.01).clamp(0.4, 1.0))

		if (wo.towers.size == 0) {
			restart()
		}

		if (player.x > 512 || player.x < 0 || player.y > 512 || player.y < 0) {
			restart()
		}

	}

	player.onCollision({ it.name == "bulletEnemy" || it.name == "tower" || it.name == "block" }) {
		it.removeFromParent()
		restart()
	}

	player.keys {
		down {
			val scale = 0.01
			if (it == Key.LEFT) rot.x = -1.0
			if (it == Key.RIGHT) rot.x = 1.0

			if (it == Key.UP) {
				addForceMult = 1.0
			}

			if (it == Key.Z) {
				var a = player.rotation - PI_HALF
				BulletPlayer(player.parent!!, wo.towers, Point(cos(a) * 4, sin(a) * 4), Point(player.x + cos(a) * 16, player.y + sin(a) * 16))
			}
		}

		up {
			if ((it == Key.LEFT) || (it == Key.RIGHT)) rot.x = 0.0

			if (it == Key.UP) {
				addForceMult = 0.0
			}
		}
	}

}

class BulletPlayer(par: Container, ens: ArrayList<Tower>, vecF: Point,pos: Point) {
	val enemies = ens
	val parent = par
	val vecForce = vecF
	init {
		var circ = Circle(1.00, Colors.RED)
		circ.name = "bulletPlayer"
		parent += circ
		circ.xy(pos.x,pos.y)
		circ.onCollision({it != parent}) {
			circ.removeFromParent()
		}
		circ.addUpdater {
			circ.x += vecForce.x
			circ.y += vecForce.y

			for (i in (enemies.size-1) downTo 0) {
				if (circ.collidesWith(enemies[i].rect)) {
					circ.removeFromParent()
					enemies[i].rect.removeFromParent()
					enemies.removeAt(i)
				}

			}

			if (circ.x > 512 || circ.x < 0 || circ.y > 512 || circ.y < 0 ) {
				circ.removeFromParent()
			}
		}
	}
}

class BulletTowers(par: Container, pl: View, vecF: Point,pos: Point) {
	val parent = par
	val vecForce = vecF
	val player = pl

	init {
		var circ = Circle(1.00, Colors.RED)
		circ.name = "bulletEnemy"
		circ.xy(pos.x,pos.y)
		parent += circ

		circ.onCollision({it.name == "block"}) {
			circ.removeFromParent()
		}

		circ.addUpdater {
			circ.x += vecForce.x
			circ.y += vecForce.y

			if (circ.x > 512 || circ.x < 0 || circ.y > 512 || circ.y < 0 ) {
				circ.removeFromParent()
			}
		}
	}
}

class Tower(parent: Container, pl: View) {
	val player: View = pl
	val width: Float = 5.0f
	val height: Float = width/2.0f
	val rect: SolidRect
	var ang: Double = 0.0
	var delayShoot: Double = Random.nextDouble()*500.0+900.0

	init {
		rect = SolidRect(width,height,Colors.RED)
		rect.name = "tower"
		parent += rect
		rect.apply {
			anchorX = 0.5
			anchorY = 1.0
		}

		rect.addHrUpdater() {t->
			if (delayShoot <= 0){
				var a = ang + Random.nextDouble()*PI_HALFD-PI_HALFD/2
				BulletTowers(parent, player, Point(cos(a.radians),sin(a.radians)), Point(cos(ang.radians)*rect.height*2 + rect.x,sin(ang.radians)*rect.height*2 + rect.y))
				delayShoot = Random.nextDouble()*500.0+900.0
			}
			delayShoot -= t/1.hrMilliseconds
		}
	}
}

class PlanetGenerator(par: Container,pl: View) {
	var rects = ArrayList<SolidRect>()
	var towers = ArrayList<Tower>()
	val parent = par
	val player = pl
	val dirX = arrayOf(1,0,-1,0)
	val dirY = arrayOf(0,-1,0,1)
	val dirAng = arrayOf(0.0,PI/2*3,PI,PI/2)

	fun generate(num: Int = 1) {
		val rang = (0..9)
		val rangRand = (0..9)
		var blocks = Array(num) {Point()}
		val worldXYCheck = Array(10){Array(10){1}}
		var l = 0
		var r = 0.3f

		fun checkNeighb(dx: Int,dy: Int): Boolean {
			var allNs = true
			for (i in (0..3)) {
				var cx = dx+dirX[i]
				var cy = dy+dirY[i]
				if (cx >= 0 && cx < worldXYCheck.size) {
					if (cy >= 0 && cy < worldXYCheck[cx].size) {
						if (worldXYCheck[cx][cy] != 1 && worldXYCheck[cx][cy] != 2) {
							return false
						}
					} else {
						return false
					}
				} else {
					return false
				}
			}
			return true
		}

		while (l < num) {
			var rx = Random.nextInt(rangRand)
			var ry = Random.nextInt(rangRand)
			if (worldXYCheck[rx][ry] == 1) {
				if (!checkNeighb(rx,ry)) {
					worldXYCheck[rx][ry] = 0
					l++
				}
			}
		}

		fun checkArray(x: Int, y: Int): Boolean {
			if (worldXYCheck.elementAtOrNull(x) != null) {
				if (worldXYCheck[x].elementAtOrNull(y) != null) {
					if (worldXYCheck[x][y] == 1) {
						return false
					} else {
						return true
					}
				} else {
					return true
				}
			} else {
				return true
			}
		}

		for (i in rang) {
			for (j in rang) {
				if (worldXYCheck[i][j] == 1) {
					var r = SolidRect(25, 25, color = Colors["#465055"])
					r.name = "block"
					r.apply {
						x = 244.0 + (i - 5) * 25
						y = 244.0 + (j - 5) * 25
					}
					parent += r
					rects.add(r)

					for (k in 0..3) {
						if (Random.nextFloat() <= 0.1f && checkArray(i+dirX[k],j+dirY[k])) {
							var b = Tower(parent, player)
							b.rect.x = r.x + r.width / 2 + dirX[k] * r.width / 2
							b.rect.y = r.y + r.height / 2 + dirY[k] * r.height / 2
							b.rect.rotation = (dirAng[k] + PI / 2).radians
							towers.add(b)
							b.ang = dirAng[k]
							parent += b.rect
						}
					}
				}
			}
		}
	}

	fun regenerate(n: Int) {
		for (i in (rects.size-1) downTo 0) {
			rects[i].removeFromParent()
			rects.removeAt(i)
		}

		for (i in (towers.size-1) downTo 0) {
			towers[i].rect.removeFromParent()
			towers.removeAt(i)
		}

		generate(n)
	}
}
