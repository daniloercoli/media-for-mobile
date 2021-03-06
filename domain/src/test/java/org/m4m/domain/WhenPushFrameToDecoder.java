/*
 * Copyright 2014-2016 Media for Mobile
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.m4m.domain;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.nio.ByteBuffer;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Mockito.verify;

public class WhenPushFrameToDecoder extends TestBase {
    private IMediaCodec mediaCodec;

    @Before
    public void setUp() {
        mediaCodec = create.mediaCodec().construct();
    }

    @Test
    public void canSetFrame() {
        Decoder decoder = create.videoDecoder().with(mediaCodec).construct();
        decoder.start();

        decoder.fillCommandQueues();
        decoder.findFreeFrame();

        verify(mediaCodec).dequeueInputBuffer(anyLong());
        verify(mediaCodec).getInputBuffers();
    }

    @Test
    public void shouldQueueInputBuffer() {
        Decoder decoder = create.videoDecoder().with(mediaCodec).construct();

        Frame frame = create
                .frame(10, 20, 30)
                .withTimeStamp(1000)
                .withInputBufferIndex(4)
                .construct();
        decoder.push(frame);

        verify(mediaCodec).queueInputBuffer(4, 0, 3, 1000, 0);
    }

    @Test
    public void shouldQueueEosFlagToInputBuffer() {
        VideoDecoder videoDecoder = create.videoDecoder().with(mediaCodec).construct();
        videoDecoder.start();

        videoDecoder.drain(4);

        verify(mediaCodec).queueInputBuffer(4, 0, 0, 0, IMediaCodec.BUFFER_FLAG_END_OF_STREAM);
    }

    @Test
    public void shouldNotTriggerHasDataCommand_AfterOnePush_IfNoOutputBuffers() {
        IMediaCodec mediaCodec = create.mediaCodec().withDequeueOutputBufferIndex(-1).construct();
        Decoder decoder = create.videoDecoder().with(mediaCodec).construct();
        assertThat(decoder.getOutputCommandQueue()).isEmpty();

        Frame frame = create.frame().construct();
        decoder.push(frame);

        assertThat(decoder.getOutputCommandQueue()).isEmpty();
    }

    @Test
    public void shouldTriggerHasDataCommand_AfterOnePush_IfThereIsOutputBuffer() {
        IMediaCodec mediaCodec = create.mediaCodec().withDequeueOutputBufferIndex(0).construct();
        Decoder decoder = create.videoDecoder().with(mediaCodec).construct();
        assertThat(decoder.getOutputCommandQueue()).isEmpty();

        Frame frame = create.frame().construct();
        decoder.push(frame);

        assertThat(decoder.getOutputCommandQueue()).equalsTo(new Pair<Command, Integer>(Command.HasData, 0), new Pair<Command, Integer>(Command.NextPair, 0));
    }

    @Test
    public void shouldPullDataFromPreviouslyDequeuedBuffer() {
        ByteBuffer expectedByteBuffer = create.byteBuffer(1, 2, 3);
        IMediaCodec mediaCodec = create
                .mediaCodec()
                .withOutputBuffer()
                .withOutputBuffer(expectedByteBuffer)
                .withDequeueOutputBufferIndex(1, -1)
                .construct();
        Decoder decoder = create.videoDecoder().with(mediaCodec).construct();
        assertThat(decoder.getOutputCommandQueue()).isEmpty();
        Frame frame = create.frame().construct();
        decoder.push(frame);

        frame = decoder.getFrame();

        Assert.assertThat(frame.getByteBuffer().array(), is(equalTo(expectedByteBuffer.array())));
    }

    @Test
    public void shouldPullEmptyFrameIfMediaCodecHasNoOutputBuffers() {
        IMediaCodec mediaCodec = create
                .mediaCodec()
                .withOutputBuffer()
                .withDequeueOutputBufferIndex(-1)
                .construct();
        Decoder decoder = create.videoDecoder().with(mediaCodec).construct();
        Frame frame = create.frame().construct();
        decoder.push(frame);

        frame = decoder.getFrame();

        assertEquals(0, frame.getLength());
        assertEquals(0, frame.getBufferIndex());
        assertEquals(0, frame.getFlags());
        assertEquals(0, frame.getTrackId());
    }

    @Test
    public void shouldGetTwoFrames() {
        IMediaCodec mediaCodec = create
                .mediaCodec()
                .withOutputBuffer(1, 1, 1)
                .withSampleTime(100)
                .withOutputBuffer(2, 2, 2)
                .withSampleTime(200)
                .withDequeueOutputBufferIndex(1, 0)
                .construct();
        Decoder decoder = create.videoDecoder().with(mediaCodec).construct();

        Frame frame = create.frame().construct();
        decoder.push(frame);
        decoder.push(frame);

        frame = decoder.getFrame();
        Assert.assertThat(frame.getByteBuffer().array(), is(equalTo(new byte[]{2, 2, 2})));
        assertEquals(200, frame.getSampleTime());

        frame = decoder.getFrame();
        Assert.assertThat(frame.getByteBuffer().array(), is(equalTo(new byte[]{1, 1, 1})));
        assertEquals(100, frame.getSampleTime());
    }

    @Test(expected = RuntimeException.class)
    public void shouldNotPushFrameToDrainingDecoder() {
        VideoDecoder videoDecoder = create.videoDecoder().construct();
        videoDecoder.start();

        videoDecoder.state = PluginState.Draining;

        videoDecoder.push(create.frame().construct());
    }

    @Test(expected = RuntimeException.class)
    public void shouldNotPushFrameToDrainedDecoder() {
        VideoDecoder videoDecoder = create.videoDecoder().construct();
        videoDecoder.start();

        videoDecoder.state = PluginState.Drained;

        videoDecoder.push(create.frame().construct());
    }

}
