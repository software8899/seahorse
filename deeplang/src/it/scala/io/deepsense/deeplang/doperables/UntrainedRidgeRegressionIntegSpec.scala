/**
 * Copyright 2015, deepsense.io
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.deepsense.deeplang.doperables

import org.apache.spark.mllib.regression.{GeneralizedLinearAlgorithm, RidgeRegressionModel, RidgeRegressionWithSGD}
import org.scalactic.EqualityPolicy.Spread

import io.deepsense.deeplang.doperables.machinelearning.ridgeregression.{UntrainedRidgeRegression, TrainedRidgeRegression}
import io.deepsense.deeplang.doperations.RidgeRegressionParameters

class UntrainedRidgeRegressionIntegSpec
  extends UntrainedRegressionIntegSpec[RidgeRegressionModel] {

  val testDataDir: String = testsDir + "/UntrainedRidgeRegressionIntegSpec"

  override def regressionName: String = "UntrainedRidgeRegression"

  override def modelType: Class[RidgeRegressionModel] = classOf[RidgeRegressionModel]

  override def constructUntrainedModel(
      untrainedModelMock: GeneralizedLinearAlgorithm[RidgeRegressionModel]): Trainable =
    UntrainedRidgeRegression(() => untrainedModelMock.asInstanceOf[RidgeRegressionWithSGD],
    mock[RidgeRegressionParameters])

  override def mockUntrainedModel(): GeneralizedLinearAlgorithm[RidgeRegressionModel] =
    mock[RidgeRegressionWithSGD]

  override val featuresValues: Seq[Spread[Double]] = Seq(
    Spread(0.0, 0.0),
    -0.755 +- 0.01,
    Spread(0.0, 0.0),
    -0.377 +- 0.01,
    Spread(0.0, 0.0),
    1.133 +- 0.01
  )

  override def validateResult(
      mockTrainedModel: RidgeRegressionModel,
      result: Scorable,
      targetColumnName: String): Registration = {
    val castedResult = result.asInstanceOf[TrainedRidgeRegression]
    castedResult.model shouldBe mockTrainedModel
    castedResult.featureColumns shouldBe Seq("column1", "column0")
    castedResult.targetColumn shouldBe targetColumnName
  }
}
