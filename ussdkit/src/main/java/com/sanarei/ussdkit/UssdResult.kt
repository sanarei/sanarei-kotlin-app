package com.sanarei.ussdkit

import android.os.Parcel
import android.os.Parcelable

sealed class UssdResult : Parcelable {
    data class Success(val response: String) : UssdResult() {
        constructor(parcel: Parcel) : this(parcel.readString() ?: "")

        override fun writeToParcel(parcel: Parcel, flags: Int) {
            parcel.writeString(response)
        }

        override fun describeContents(): Int = 0

        companion object CREATOR : Parcelable.Creator<Success> {
            override fun createFromParcel(parcel: Parcel): Success = Success(parcel)
            override fun newArray(size: Int): Array<Success?> = arrayOfNulls(size)
        }
    }

    data class Failure(val error: String) : UssdResult() {
        constructor(parcel: Parcel) : this(parcel.readString() ?: "")

        override fun writeToParcel(parcel: Parcel, flags: Int) {
            parcel.writeString(error)
        }

        override fun describeContents(): Int = 0

        companion object CREATOR : Parcelable.Creator<Failure> {
            override fun createFromParcel(parcel: Parcel): Failure = Failure(parcel)
            override fun newArray(size: Int): Array<Failure?> = arrayOfNulls(size)
        }
    }

    data class Intermediate(val response: String) : UssdResult() {
        constructor(parcel: Parcel) : this(parcel.readString() ?: "")

        override fun writeToParcel(parcel: Parcel, flags: Int) {
            parcel.writeString(response)
        }

        override fun describeContents(): Int = 0

        companion object CREATOR : Parcelable.Creator<Intermediate> {
            override fun createFromParcel(parcel: Parcel): Intermediate = Intermediate(parcel)
            override fun newArray(size: Int): Array<Intermediate?> = arrayOfNulls(size)
        }
    }
}