import com.qifun.statelessFuture.Future
import java.nio.channels.AsynchronousSocketChannel
import com.qifun.statelessFuture.io.Nio2Future
import java.nio.ByteBuffer
import java.net.InetSocketAddress
import java.io.IOException
import java.nio.charset.Charset
import scala.util.control.Exception.Catcher

object HttpClientExample {
  def main(arguments: Array[String]) {

    def writeAll(socket: AsynchronousSocketChannel, buffer: ByteBuffer) = Future {
      while (buffer.remaining > 0) {
        Nio2Future.write(socket, buffer).await
      }
    }

    def readAll(socket: AsynchronousSocketChannel, buffer: ByteBuffer): Future[Unit] = Future {
      if (buffer.remaining > 0) {
        Nio2Future.read(socket, buffer).await.intValue match {
          case -1 =>
          case _ => readAll(socket, buffer).await
        }
      } else {
        throw new IOException("The response is too big to read.")
      }
    }

    val httpFuture = Future {
      val socket = AsynchronousSocketChannel.open()
      try {
        Nio2Future.connect(socket, new InetSocketAddress("www.qifun.com", 80)).await
        val request = ByteBuffer.wrap("GET / HTTP/1.1\r\nHost:www.qifun.com\r\nConnection:Close\r\n\r\n".getBytes)
        writeAll(socket, request).await
        val response = ByteBuffer.allocate(100000)
        readAll(socket, response).await
        response.flip()
        Charset.forName("UTF-8").decode(response).toString
      } finally {
        socket.close()
      }
    }

    implicit def catcher: Catcher[Unit] = {
      case e: Exception => {
        println(s"Error when downloading http://ww.qifun.com/:\n${e.getMessage}")
      }
    }

    @volatile
    var isDone = false
    val lock = new AnyRef

    for (httpResponse <- httpFuture) {
      println(s"The response from http://www.qifun.com/ is:\n$httpResponse")
      lock.synchronized {
        isDone = true
        lock.notify()
      }
    }

    lock.synchronized { while (!isDone) { lock.wait() } }

  }

}