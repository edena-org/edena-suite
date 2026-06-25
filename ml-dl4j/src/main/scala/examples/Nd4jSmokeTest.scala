package examples

import org.deeplearning4j.nn.conf.NeuralNetConfiguration
import org.deeplearning4j.nn.conf.layers.{DenseLayer, OutputLayer}
import org.deeplearning4j.nn.multilayer.MultiLayerNetwork
import org.deeplearning4j.nn.weights.WeightInit
import org.nd4j.linalg.activations.Activation
import org.nd4j.linalg.dataset.DataSet
import org.nd4j.linalg.factory.Nd4j
import org.nd4j.linalg.learning.config.Adam
import org.nd4j.linalg.lossfunctions.LossFunctions

/**
  * Minimal smoke test verifying the ND4J native backend (javacpp-presets 1.5.7) loads and computes,
  * and that a tiny DL4J MultiLayerNetwork can be built and trained — the runtime check that matters
  * for the DL4J 1.0.0-M1.1 -> M2.1 native-library version bump.
  */
object Nd4jSmokeTest extends App {

  println("=== ND4J backend smoke test ===")
  println(s"Backend:      ${Nd4j.getBackend.getClass.getName}")
  println(s"Data type:    ${Nd4j.dataType()}")

  // 1) Native matrix multiply: [[1,2],[3,4]] x [[5,6],[7,8]] = [[19,22],[43,50]]
  val a = Nd4j.create(Array(1.0f, 2.0f, 3.0f, 4.0f), Array(2, 2))
  val b = Nd4j.create(Array(5.0f, 6.0f, 7.0f, 8.0f), Array(2, 2))
  val c = a.mmul(b)
  println(s"a·b =\n$c")
  assert(c.getFloat(0L) == 19.0f && c.getFloat(1L) == 22.0f &&
         c.getFloat(2L) == 43.0f && c.getFloat(3L) == 50.0f, s"matrix multiply wrong: $c")
  println("[ok] native mmul correct")

  // 2) Tiny DL4J network: train a 2-input XOR-ish classifier for a few iterations and check loss drops.
  val conf = new NeuralNetConfiguration.Builder()
    .seed(42)
    .weightInit(WeightInit.XAVIER)
    .updater(new Adam(0.1))
    .list()
    .layer(0, new DenseLayer.Builder().nIn(2).nOut(8).activation(Activation.RELU).build())
    .layer(1, new OutputLayer.Builder(LossFunctions.LossFunction.NEGATIVELOGLIKELIHOOD)
      .nIn(8).nOut(2).activation(Activation.SOFTMAX).build())
    .build()

  val net = new MultiLayerNetwork(conf)
  net.init()

  val features = Nd4j.create(Array(0f,0f, 0f,1f, 1f,0f, 1f,1f), Array(4, 2))
  val labels   = Nd4j.create(Array(1f,0f, 0f,1f, 0f,1f, 1f,0f), Array(4, 2)) // XOR one-hot
  val ds = new DataSet(features, labels)

  val firstScore = { net.fit(ds); net.score() }
  (0 until 200).foreach(_ => net.fit(ds))
  val lastScore = net.score()
  println(f"[info] score: first=$firstScore%.4f last=$lastScore%.4f")
  assert(lastScore < firstScore, s"training did not reduce loss ($firstScore -> $lastScore)")
  println("[ok] DL4J MultiLayerNetwork trained, loss decreased")

  println("=== SMOKE TEST PASSED ===")
}
