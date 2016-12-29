from pandas_datareader import data
from pykalman import KalmanFilter
import numpy
import pandas
import os
from matplotlib import pyplot


def main():
    pyplot.style.use('ggplot')

    secs = ['EWA', 'EWC']
    # get adjusted close prices from Yahoo
    prices_path = 'prices.pkl'
    if os.path.exists(prices_path):
        print('loading from cache')
        prices = pandas.read_pickle(prices_path)

    else:
        print('loading from web')
        prices = data.DataReader(secs, 'yahoo', '2011-12-29', '2016-12-28')['Adj Close']
        prices.to_pickle('prices.pkl')

    # visualize the correlation between assest prices over time
    cm = pyplot.cm.get_cmap('jet')
    count = prices['EWA'].count()
    colors = numpy.linspace(0.1, 1, count)
    sc = pyplot.scatter(prices[prices.columns[0]], prices[prices.columns[1]], s=30, c=colors, cmap=cm, edgecolor='k', alpha=0.7)
    cb = pyplot.colorbar(sc)
    cb.ax.set_yticklabels([p.date() for p in prices[::count//9].index])
    pyplot.xlabel(prices.columns[0])
    pyplot.ylabel(prices.columns[1])

    delta = 1e-5
    trans_cov = delta / (1 - delta) * numpy.eye(2)
    obs_mat = numpy.vstack([prices['EWA'], numpy.ones(prices['EWA'].shape)]).T[:, numpy.newaxis]

    kf = KalmanFilter(n_dim_obs=1, n_dim_state=2,
                      initial_state_mean=numpy.zeros(2),
                      initial_state_covariance=numpy.ones((2, 2)),
                      transition_matrices=numpy.eye(2),
                      observation_matrices=obs_mat,
                      observation_covariance=1.0,
                      transition_covariance=trans_cov)

    state_means, state_covs = kf.filter(prices['EWC'].values)
    results = {'slope': state_means[:, 0], 'intercept': state_means[:, 1]}
    output_df = pandas.DataFrame(results, index=prices.index)
    output_df.plot(subplots=True)
    pyplot.show()
    #pyplot.tight_layout()

    # visualize the correlation between assest prices over time
    cm = pyplot.cm.get_cmap('jet')
    colors = numpy.linspace(0.1, 1, count)
    sc = pyplot.scatter(prices[prices.columns[0]], prices[prices.columns[1]], s=50, c=colors, cmap=cm, edgecolor='k', alpha=0.7)
    cb = pyplot.colorbar(sc)
    cb.ax.set_yticklabels([p.date() for p in prices[::count//9].index])
    pyplot.xlabel(prices.columns[0])
    pyplot.ylabel(prices.columns[1])

    # add regression lines
    step = 20
    xi = numpy.linspace(prices[prices.columns[0]].min(), prices[prices.columns[0]].max(), 2)
    count_states = state_means[::step].size
    colors_l = numpy.linspace(0.1, 1, count_states)
    i = 0
    for beta in state_means[::step]:
        pyplot.plot(xi, beta[0] * xi + beta[1], alpha=.2, lw=1, c=cm(colors_l[i]))
        i += 1

    pyplot.show()
    print(output_df)

if __name__ == '__main__':
    main()
