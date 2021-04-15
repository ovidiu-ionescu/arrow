package arrow.optics.combinators

import arrow.core.Either
import arrow.core.Eval
import arrow.core.Ior
import arrow.core.merge
import arrow.core.unalign
import arrow.optics.FoldK
import arrow.optics.IxAffineTraversal
import arrow.optics.IxFold
import arrow.optics.IxFoldF
import arrow.optics.IxLens
import arrow.optics.IxTraversal
import arrow.optics.Lens
import arrow.optics.Optic
import arrow.optics.PIxAffineTraversal
import arrow.optics.PIxLens
import arrow.optics.PIxTraversal
import arrow.optics.PLens
import arrow.optics.TraversalK
import arrow.optics.collectOf
import arrow.optics.compose
import arrow.optics.firstOrNull
import arrow.optics.foldLazyOf
import arrow.optics.internal.Applicative
import arrow.optics.internal.IxWanderF
import arrow.optics.internal.Kind
import arrow.optics.internal.Pro
import arrow.optics.internal.Profunctor
import arrow.optics.internal.backwards
import arrow.optics.ixATraversing
import arrow.optics.ixCollectOf
import arrow.optics.ixFirstOrNull
import arrow.optics.ixFolding
import arrow.optics.ixLens
import arrow.optics.ixModifyOrNull
import arrow.optics.ixTraverseLazyOf
import arrow.optics.ixTraverseLazyOf_
import arrow.optics.ixTraverseOf
import arrow.optics.ixTraverseOf_
import arrow.optics.ixTraversing
import arrow.optics.ixView
import arrow.optics.lens
import arrow.optics.modify
import arrow.optics.modifyOrNull
import arrow.optics.set
import arrow.optics.traverseLazyOf
import arrow.optics.traverseOf
import arrow.optics.traversing
import arrow.optics.view
import arrow.typeclasses.Monoid

fun <K : TraversalK, I, S, A> Optic<K, I, S, S, A, A>.partsOf(): Lens<S, List<A>> =
  Optic.lens({ s ->
    s.collectOf(this)
  }, { s: S, xs: List<A> ->
    val buf = xs.toMutableList()
    val ys = s.modify(this) {
      if (buf.isEmpty()) it
      else buf.removeFirst()
    }
    ys
  })

fun <K : TraversalK, I, S, T, A, B> Optic<K, I, S, T, A, B>.unsafePartsOf(): PLens<S, T, List<A>, List<B>> =
  Optic.lens({ s ->
    s.collectOf(this as Optic<K, I, S, S, A, A>)
  }, { s: S, xs: List<B> ->
    val buf = xs.toMutableList()
    s.modify(this) {
      if (buf.isEmpty()) throw IllegalStateException("Optic.unsafePartsOf empty list! Make sure to not modify the list size, or at least guarantee it is not smaller.")
      else buf.removeFirst()
    }
  })

fun <K : TraversalK, I, S, A> Optic<K, I, S, S, A, A>.ixPartsOf(): IxLens<List<I>, S, List<A>> =
  Optic.ixLens({ s ->
    s.ixCollectOf(this).unalign { Ior.Both(it.first, it.second) }
  }, { s: S, xs: List<A> ->
    val buf = xs.toMutableList()
    val ys = s.modify(this) {
      if (buf.isEmpty()) it
      else buf.removeFirst()
    }
    ys
  })

fun <K : TraversalK, I, S, T, A, B> Optic<K, I, S, T, A, B>.unsafeIxPartsOf(): PIxLens<List<I>, S, T, List<A>, List<B>> =
  Optic.ixLens({ s ->
    s.ixCollectOf(this as Optic<K, I, S, S, A, A>)
      .unalign { Ior.Both(it.first, it.second) }
  }, { s: S, xs: List<B> ->
    val buf = xs.toMutableList()
    s.modify(this) {
      if (buf.isEmpty()) throw IllegalStateException("Optic.unsafeIxPartsOf empty list! Make sure to not modify the list size, or at least guarantee it is not smaller.")
      else buf.removeFirst()
    }
  })


fun <K : TraversalK, I, S, T, A> Optic<K, I, S, T, A, A>.take(n: Int): PIxTraversal<I, S, T, A, A> =
  Optic.ixTraversing(object : IxWanderF<I, S, T, A, A> {
    override fun <F> invoke(AF: Applicative<F>, source: S, f: (I, A) -> Kind<F, A>): Kind<F, T> {
      var counter = 0
      return source.ixTraverseOf(this@take, AF) { i, a ->
        if (counter++ < n) f(i, a)
        else AF.pure(a)
      }
    }

    override fun <F> invokeLazy(AF: Applicative<F>, source: S, f: (I, A) -> Kind<F, A>): Kind<F, T> {
      var counter = 0
      return source.ixTraverseLazyOf(this@take, AF) { i, a ->
        if (counter++ < n) f(i, a)
        else AF.pure(a)
      }
    }
  })

fun <K : TraversalK, I, S, T, A> Optic<K, I, S, T, A, A>.drop(n: Int): PIxTraversal<I, S, T, A, A> =
  Optic.ixTraversing(object : IxWanderF<I, S, T, A, A> {
    override fun <F> invoke(AF: Applicative<F>, source: S, f: (I, A) -> Kind<F, A>): Kind<F, T> {
      var counter = 0
      return source.ixTraverseOf(this@drop, AF) { i, a ->
        if (counter++ < n) AF.pure(a)
        else f(i, a)
      }
    }

    override fun <F> invokeLazy(AF: Applicative<F>, source: S, f: (I, A) -> Kind<F, A>): Kind<F, T> {
      var counter = 0
      return source.ixTraverseLazyOf(this@drop, AF) { i, a ->
        if (counter++ < n) AF.pure(a)
        else f(i, a)
      }
    }
  })

fun <K : TraversalK, I, S, T, A> Optic<K, I, S, T, A, A>.takeWhile(filter: (A) -> Boolean): PIxTraversal<I, S, T, A, A> =
  Optic.ixTraversing(object : IxWanderF<I, S, T, A, A> {
    override fun <F> invoke(AF: Applicative<F>, source: S, f: (I, A) -> Kind<F, A>): Kind<F, T> {
      var tripped = false
      return source.ixTraverseOf(this@takeWhile, AF) { i, a ->
        if (!tripped && filter(a)) f(i, a)
        else AF.pure(a).also { tripped = true }
      }
    }

    override fun <F> invokeLazy(AF: Applicative<F>, source: S, f: (I, A) -> Kind<F, A>): Kind<F, T> {
      var tripped = false
      return source.ixTraverseLazyOf(this@takeWhile, AF) { i, a ->
        if (!tripped && filter(a)) f(i, a)
        else AF.pure(a).also { tripped = true }
      }
    }
  })

fun <K : TraversalK, I, S, T, A> Optic<K, I, S, T, A, A>.dropWhile(filter: (A) -> Boolean): PIxTraversal<I, S, T, A, A> =
  Optic.ixTraversing(object : IxWanderF<I, S, T, A, A> {
    override fun <F> invoke(AF: Applicative<F>, source: S, f: (I, A) -> Kind<F, A>): Kind<F, T> {
      var tripped = false
      return source.ixTraverseOf(this@dropWhile, AF) { i, a ->
        if (!tripped && filter(a)) AF.pure(a).also { tripped = true }
        else f(i, a)
      }
    }

    override fun <F> invokeLazy(AF: Applicative<F>, source: S, f: (I, A) -> Kind<F, A>): Kind<F, T> {
      var tripped = false
      return source.ixTraverseLazyOf(this@dropWhile, AF) { i, a ->
        if (!tripped && filter(a)) AF.pure(a).also { tripped = true }
        else f(i, a)
      }
    }
  })

fun <K : TraversalK, I, S, T, A> Optic<K, I, S, T, A, A>.ixTakeWhile(filter: (I, A) -> Boolean): PIxTraversal<I, S, T, A, A> =
  Optic.ixTraversing(object : IxWanderF<I, S, T, A, A> {
    override fun <F> invoke(AF: Applicative<F>, source: S, f: (I, A) -> Kind<F, A>): Kind<F, T> {
      var tripped = false
      return source.ixTraverseOf(this@ixTakeWhile, AF) { i, a ->
        if (!tripped && filter(i, a)) f(i, a)
        else AF.pure(a).also { tripped = true }
      }
    }

    override fun <F> invokeLazy(AF: Applicative<F>, source: S, f: (I, A) -> Kind<F, A>): Kind<F, T> {
      var tripped = false
      return source.ixTraverseLazyOf(this@ixTakeWhile, AF) { i, a ->
        if (!tripped && filter(i, a)) f(i, a)
        else AF.pure(a).also { tripped = true }
      }
    }
  })

fun <K : TraversalK, I, S, T, A> Optic<K, I, S, T, A, A>.ixDropWhile(filter: (I, A) -> Boolean): PIxTraversal<I, S, T, A, A> =
  Optic.ixTraversing(object : IxWanderF<I, S, T, A, A> {
    override fun <F> invoke(AF: Applicative<F>, source: S, f: (I, A) -> Kind<F, A>): Kind<F, T> {
      var tripped = false
      return source.ixTraverseOf(this@ixDropWhile, AF) { i, a ->
        if (!tripped && filter(i, a)) AF.pure(a).also { tripped = true }
        else f(i, a)
      }
    }

    override fun <F> invokeLazy(AF: Applicative<F>, source: S, f: (I, A) -> Kind<F, A>): Kind<F, T> {
      var tripped = false
      return source.ixTraverseLazyOf(this@ixDropWhile, AF) { i, a ->
        if (!tripped && filter(i, a)) AF.pure(a).also { tripped = true }
        else f(i, a)
      }
    }
  })

// This is hacked together using unsafePartsOf in an index preserving way
// TODO If we use lazy applicatives instead we can just use Applicative.backwards instead, but for now this is needed
fun <K : TraversalK, I, S, T, A, B> Optic<K, I, S, T, A, B>.backwards(): PIxTraversal<I, S, T, A, B> =
  Optic.ixTraversing(object : IxWanderF<I, S, T, A, B> {
    override fun <F> invoke(AF: Applicative<F>, source: S, f: (I, A) -> Kind<F, B>): Kind<F, T> {
      val thisP = this@backwards.unsafeIxPartsOf()
      val (ix, parts) = source.ixView(thisP).let { (a, b) -> a to b.toMutableList() }
      val buf = mutableListOf<B>()
      val fUnit = parts.asReversed().foldIndexed(AF.pure(Unit)) { i, acc, a ->
        AF.ap(
          AF.map(acc) { { b -> buf += b } },
          f(ix[ix.size - i - 1], a)
        )
      }
      return AF.map(fUnit) { source.modify(thisP) { buf } }
    }
  })

fun <K : TraversalK, I, S, A> Optic<K, I, S, S, A, A>.singular(): IxAffineTraversal<I, S, A> =
  take(1) as IxAffineTraversal<I, S, A>

// Unsafe because it throws if the traversal is empty and modifies all elements if it has more than one
fun <K : TraversalK, I, S, T, A, B> Optic<K, I, S, T, A, B>.unsafeSingular(): PIxLens<I, S, T, A, B> =
  Optic.ixLens({ s ->
    s.ixFirstOrNull(this@unsafeSingular) ?: throw IllegalStateException("unsafeSingular: No element")
  }, { s, b ->
    s.set(this@unsafeSingular, b)
  })

@JvmName("failing_traversal")
fun <K1 : TraversalK, K2 : TraversalK, I, J, S, T, A, B> Optic<K1, I, S, T, A, B>.failing(
  other: Optic<K2, J, S, T, A, B>
): Optic<TraversalK, Either<I, J>, S, T, A, B> =
  Optic.ixTraversing(object : IxWanderF<Either<I, J>, S, T, A, B> {
    override fun <F> invoke(AF: Applicative<F>, source: S, f: (Either<I, J>, A) -> Kind<F, B>): Kind<F, T> {
      var tripped = false
      val res = source.ixTraverseOf(this@failing, AF) { i, a -> f(Either.Left(i), a).also { tripped = true } }

      return if (tripped) res
      else source.ixTraverseOf(other, AF) { j, a -> f(Either.Right(j), a) }
    }

    override fun <F> invokeLazy(AF: Applicative<F>, source: S, f: (Either<I, J>, A) -> Kind<F, B>): Kind<F, T> {
      var tripped = false
      val res = source.ixTraverseLazyOf(this@failing, AF) { i, a -> f(Either.Left(i), a).also { tripped = true } }

      return if (tripped) res
      else source.ixTraverseLazyOf(other, AF) { j, a -> f(Either.Right(j), a) }
    }
  })

@JvmName("failing_fold")
fun <K1 : FoldK, K2 : FoldK, I, J, S, T, A, B> Optic<K1, I, S, T, A, B>.failing(
  other: Optic<K2, J, S, T, A, B>
): Optic<FoldK, Either<I, J>, S, Nothing, A, Nothing> =
  Optic.ixFolding(object : IxFoldF<Either<I, J>, S, A> {
    override fun <F> invoke(AF: Applicative<F>, s: S, f: (Either<I, J>, A) -> Kind<F, Unit>): Kind<F, Unit> {
      var tripped = false
      val res = s.ixTraverseOf_(this@failing, AF) { i, a -> f(Either.Left(i), a).also { tripped = true } }

      return if (tripped) res
      else s.ixTraverseOf_(other, AF) { j, a -> f(Either.Right(j), a) }
    }

    override fun <F> invokeLazy(AF: Applicative<F>, s: S, f: (Either<I, J>, A) -> Kind<F, Unit>): Kind<F, Unit> {
      var tripped = false
      val res = s.ixTraverseLazyOf_(this@failing, AF) { i, a -> f(Either.Left(i), a).also { tripped = true } }

      return if (tripped) res
      else s.ixTraverseLazyOf_(other, AF) { j, a -> f(Either.Right(j), a) }
    }
  })

// This is technically just compose(next.failing(this.deepOf(next))) however that has two problems in kotlin:
// - recursive on call to deepOf: this.deepOf(next) is evaluated before any traversing takes place ==> stackoverflow
//  - this can be solved by making the argument to failing lazy
// - recursive on traversal: for every s visited by this we get additional of stack depth ==> stackoverflow
//  - this is solved here by manually implementing the combinator in a flat (and rather ugly) way
@JvmName("deepOf_traversal")
fun <K1 : TraversalK, K2 : TraversalK, I, S, T, A, B> Optic<K1, Any?, S, T, S, T>.deepOf(
  next: Optic<K2, I, S, T, A, B>
): PIxTraversal<I, S, T, A, B> =
  Optic.ixTraversing(object : IxWanderF<I, S, T, A, B> {
    override fun <F> invoke(AF: Applicative<F>, source: S, f: (I, A) -> Kind<F, B>): Kind<F, T> {
      var tripped = false
      val nextS = mutableListOf(source)
      var ft: Kind<F, T>? = null
      while (nextS.isNotEmpty()) {
        val head = nextS.removeFirst()
        ft = head.ixTraverseOf(next, AF) { i, a -> f(i, a).also { tripped = true } }

        if (tripped) break
        else nextS.addAll(head.collectOf(this@deepOf))
      }
      return ft!!
    }
    override fun <F> invokeLazy(AF: Applicative<F>, source: S, f: (I, A) -> Kind<F, B>): Kind<F, T> {
      var tripped = false
      val nextS = mutableListOf(source)
      var ft: Kind<F, T>? = null
      while (nextS.isNotEmpty()) {
        val head = nextS.removeFirst()
        ft = head.ixTraverseLazyOf(next, AF) { i, a -> f(i, a).also { tripped = true } }

        if (tripped) break
        else nextS.addAll(head.collectOf(this@deepOf))
      }
      return ft!!
    }
  })

@JvmName("deepOf_fold")
fun <K1 : FoldK, K2 : FoldK, I, S, T, A, B> Optic<K1, Any?, S, T, S, T>.deepOf(
  next: Optic<K2, I, S, T, A, B>
): IxFold<I, S, A> =
  Optic.ixFolding(object : IxFoldF<I, S, A> {
    override fun <F> invoke(AF: Applicative<F>, s: S, f: (I, A) -> Kind<F, Unit>): Kind<F, Unit> {
      var tripped = false
      val nextS = mutableListOf(s)
      var ft: Kind<F, Unit>? = null
      while (nextS.isNotEmpty()) {
        val head = nextS.removeFirst()
        ft = head.ixTraverseOf_(next, AF) { i, a -> f(i, a).also { tripped = true } }

        if (tripped) break
        else nextS.addAll(head.collectOf(this@deepOf))
      }
      return ft!!
    }
    override fun <F> invokeLazy(AF: Applicative<F>, s: S, f: (I, A) -> Kind<F, Unit>): Kind<F, Unit> {
      var tripped = false
      val nextS = mutableListOf(s)
      var ft: Kind<F, Unit>? = null
      while (nextS.isNotEmpty()) {
        val head = nextS.removeFirst()
        ft = head.ixTraverseLazyOf_(next, AF) { i, a -> f(i, a).also { tripped = true } }

        if (tripped) break
        else nextS.addAll(head.collectOf(this@deepOf))
      }
      return ft!!
    }
  })

