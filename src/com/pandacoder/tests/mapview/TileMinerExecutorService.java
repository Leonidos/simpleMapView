package com.pandacoder.tests.mapview;

import java.util.HashSet;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Выполняет задания в пуле потока фиксированного размера 
 * corePoolSize == maximumPoolSize == poolSize == true.
 * Очередь заданий реализована на LinkedBlockingQueue(1) 
 * Только одно задание может ожидать выполнения в очереди
 * 
 * Задания должны быть потомками {@link#TileMinerRunnable}. 
 * Реализована защита от параллельного скачивания одинаковых
 * тайлов, при работе в несколько потоков.
 * 
 */
public class TileMinerExecutorService extends ThreadPoolExecutor {
	
	/**
	 * Абстрактная работа по добыче тайла. Перед выполненим работы
	 * проверить не отсменен ли данный запрос (isCanceled() == true),
	 * если работа отменена ничего не делать и завершить задание
	 * 
	 */
	public static abstract class TileMinerRunnable implements Runnable {
		
		private boolean canceled = false;
		protected final TileRequest tileRequest;
		
		public TileMinerRunnable(TileRequest tileRequest) {
			this.tileRequest = tileRequest;
		}
		
		private void cancel() {
			canceled = true;
		}
		
		/**
		 * Возвращает флан отмены текущего задания.
		 * TileMinerExecutorService может отменять некоторые задания, в случае
		 * если такое же задание уже выполняется в другом потоке.
		 * 
		 * @return true - если задание отменено, false - в противном случае
		 */
		final protected boolean isCanceled() {
			return canceled;
		}

		@Override
		final public boolean equals(Object o) {
			
			if (o instanceof TileMinerRunnable) {
				TileMinerRunnable other = (TileMinerRunnable)o;
				return tileRequest.equals(other.tileRequest);
			}

			return false;
		}
		@Override
		final public int hashCode() {
			return tileRequest.hashCode();
		}	
	}
	
	// Сет текущих выполняемых заданий
	private final HashSet<TileMinerRunnable> runningTileMiningRequests;
	
	/**
	 * Создает TileMinerExecutorService
	 * @param poolSize размер пула потоков
	 */
	public TileMinerExecutorService(int poolSize) {
		super(poolSize, poolSize, 0, TimeUnit.SECONDS, new ArrayBlockingQueue<Runnable>(1));
		if (poolSize > 1)	runningTileMiningRequests = new HashSet<TileMinerRunnable>(poolSize);
		else 				runningTileMiningRequests = null;
	}	

	@Override
	protected void afterExecute(Runnable r, Throwable t) {
		super.afterExecute(r, t);
		
		if (runningTileMiningRequests != null) {
			TileMinerRunnable runnable = (TileMinerRunnable)r;
			synchronized(runningTileMiningRequests) {
				if (runnable.isCanceled() == false) {
					runningTileMiningRequests.remove((TileMinerRunnable)r);
				}
			}
		}
	}

	@Override
	protected void beforeExecute(Thread t, Runnable r) {
		if (runningTileMiningRequests != null) {
			TileMinerRunnable runnable = (TileMinerRunnable)r;
			synchronized(runningTileMiningRequests) {
				if (runningTileMiningRequests.contains(runnable) == true) {
					runnable.cancel();
				} else if (runnable.isCanceled() == false) {
					runningTileMiningRequests.add(runnable);
				}
			}
		}

		super.beforeExecute(t, r);
	}
}
