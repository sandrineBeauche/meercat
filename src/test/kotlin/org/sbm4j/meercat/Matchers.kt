package org.sbm4j.meercat

import com.natpryce.hamkrest.Matcher
import com.natpryce.hamkrest.allOf
import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.has
import com.natpryce.hamkrest.hasElement
import com.natpryce.hamkrest.hasSize
import com.natpryce.hamkrest.isA
import com.natpryce.hamkrest.sameInstance
import org.sbm4j.meercat.data.Status
import org.sbm4j.meercat.data.TestingBack
import org.sbm4j.meercat.data.TestingSend

fun testingSendWithValue(value: String) =
    isA<TestingSend>(
        has(TestingSend::value,
            equalTo(value)
        )
    )

fun testingBack(send: TestingSend) =
    isA<TestingBack>(
        allOf(
            has(
                TestingBack::send,
                sameInstance(send)
            ),
            has(
                TestingBack::status,
                equalTo(Status.OK)
            )
        )

    )

fun testingBack(value: String) =
    isA<TestingBack>(
        has(
            TestingBack::send,
            testingSendWithValue(value)
        )
    )

fun testingBackWithErrors(value: String, status: Status, nbErrors: Int) =
    isA<TestingBack>(
        allOf(
            has(
                TestingBack::send, testingSendWithValue(value)
            ),
            has(
                TestingBack::status, equalTo(status)
            ),
            has(
                TestingBack::errorInfos,
                hasSize(equalTo(nbErrors))
            )
        )
    )

fun <T> hasElements(vararg elements: T): Matcher<Collection<T>> =
    allOf(
        elements.map { hasElement(it) }
    )