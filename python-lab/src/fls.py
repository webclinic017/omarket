import math

import logging
import numpy


class FlexibleLeastSquare(object):

    def __init__(self,
                 initial_state_mean, initial_state_covariance,
                 observation_covariance, trans_cov):

        self.beta = numpy.matrix(initial_state_mean).transpose()
        self.cov_beta_prediction = numpy.matrix(initial_state_covariance)
        self.v_omega = numpy.matrix(trans_cov)
        #
        self.v_epsilon = observation_covariance
        self.first_round = True
        self.cov_beta = None

    class Result(object):
        def __init__(self, estimated_output, beta, var_output_error, error):
            self.estimated_output = estimated_output
            self.beta = [value[0] for value in beta.tolist()]
            self.var_output_error = var_output_error
            self.error = error

    def estimate(self, output_value, inputs):
        """

        :param output: scalar value of measured output
        :param inputs: list of measured input values
        :return:
        """
        if not self.first_round:
            self.cov_beta_prediction = self.cov_beta + self.v_omega

        factors = numpy.matrix(inputs)
        output = numpy.matrix([output_value])
        output_estimated = numpy.dot(factors, self.beta)
        output_error = output - output_estimated
        var_output_error = numpy.dot(numpy.dot(factors, self.cov_beta_prediction), factors.transpose()) + self.v_epsilon
        kalman_gain = numpy.dot(self.cov_beta_prediction, factors.transpose()) / var_output_error
        self.beta += kalman_gain * output_error.item()
        self.cov_beta = self.cov_beta_prediction - numpy.dot(numpy.dot(kalman_gain, factors), self.cov_beta_prediction)
        if self.first_round:
            self.first_round = False
            result = FlexibleLeastSquare.Result(output_value, self.beta, 0., 0.)

        else:
            result = FlexibleLeastSquare.Result(output_estimated.item(), self.beta, var_output_error, output_error.item())

        return result


class DynamicLinearRegression(FlexibleLeastSquare):
    def __init__(self, size, delta, v_epsilon):
        initial_state_covariance = numpy.ones((size, size))
        observation_covariance = v_epsilon
        initial_state_mean = numpy.zeros(size)
        trans_cov = delta / (1. - delta) * numpy.eye(size)
        super().__init__(initial_state_mean, initial_state_covariance,
                         observation_covariance, trans_cov)