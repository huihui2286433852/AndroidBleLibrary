package com.scin.blelibrary.ext

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock


/**
 * @author  linXQ
 */

fun Mutex.lockAsyMethod(scope: CoroutineScope = CoroutineScope(Dispatchers.IO),method: () -> Unit){
    scope.launch(Dispatchers.IO) {
        runBlocking {
            this@lockAsyMethod.withLock {
                method()
            }
        }
    }

}
