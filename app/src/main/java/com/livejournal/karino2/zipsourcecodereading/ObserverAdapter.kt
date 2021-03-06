package com.livejournal.karino2.zipsourcecodereading

import android.support.v7.widget.RecyclerView
import io.reactivex.Observable
import io.reactivex.subjects.PublishSubject

/**
 * Created by _ on 2017/08/29.
 */
abstract class ObserverAdapter<T, VH : RecyclerView.ViewHolder> : RecyclerView.Adapter<VH>() {
    override fun getItemCount(): Int {
        return items.size
    }

    val items = ArrayList<T>()

    fun addAll(more: List<T>) {
        items.addAll(more)
        subject.onNext(this)
    }

    val subject :  PublishSubject<ObserverAdapter<T, VH>> = PublishSubject.create()

    fun datasetChangedNotifier() : Observable<ObserverAdapter<T, VH>> {
        return subject
    }

}