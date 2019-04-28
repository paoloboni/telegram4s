/*
 * Copyright (c) 2019 Paolo Boni
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package org.telegram.bot.kernel;

import org.telegram.api.bot.TLBotInfo;
import org.telegram.api.chat.TLAbsChatFull;
import org.telegram.api.chat.invite.TLAbsChatInvite;
import org.telegram.api.chat.participant.chatparticipants.TLAbsChatParticipants;
import org.telegram.api.peer.notify.settings.TLAbsPeerNotifySettings;
import org.telegram.api.photo.TLAbsPhoto;
import org.telegram.tl.StreamingUtils;
import org.telegram.tl.TLContext;
import org.telegram.tl.TLVector;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * This class is a workaround to fix the issue https://github.com/rubenlagus/TelegramApi/issues/56
 * while the PR https://github.com/rubenlagus/TelegramApi/pull/57 is not merged
 */
public class TLChatFull extends TLAbsChatFull {
    /**
     * The constant CLASS_ID.
     */
    public static final int CLASS_ID = 0x2e02a614;

    private TLAbsChatParticipants participants; ///< List of chat participants
    private TLVector<TLBotInfo> botInfo = new TLVector<>();

    /**
     * Instantiates a new TL chat full.
     */
    public TLChatFull() {
        super();
    }

    public int getClassId() {
        return CLASS_ID;
    }

    /**
     * Gets participants.
     *
     * @return the participants
     */
    public TLAbsChatParticipants getParticipants() {
        return this.participants;
    }

    /**
     * Sets participants.
     *
     * @param value the value
     */
    public void setParticipants(TLAbsChatParticipants value) {
        this.participants = value;
    }

    public void serializeBody(OutputStream stream)
            throws IOException {
        StreamingUtils.writeInt(this.id, stream);
        StreamingUtils.writeTLObject(this.participants, stream);
        StreamingUtils.writeTLObject(this.photo, stream);
        StreamingUtils.writeTLObject(this.notifySettings, stream);
        StreamingUtils.writeTLObject(this.exportedInvite, stream);
        StreamingUtils.writeTLVector(this.botInfo, stream);
    }

    public void deserializeBody(InputStream stream, TLContext context)
            throws IOException {
        this.id = StreamingUtils.readInt(stream);
        this.participants = ((TLAbsChatParticipants) StreamingUtils.readTLObject(stream, context));
        this.photo = ((TLAbsPhoto) StreamingUtils.readTLObject(stream, context));
        this.notifySettings = ((TLAbsPeerNotifySettings) StreamingUtils.readTLObject(stream, context));
        this.exportedInvite = ((TLAbsChatInvite) StreamingUtils.readTLObject(stream, context));
        this.botInfo = (TLVector<TLBotInfo>) StreamingUtils.readTLVector(stream, context);
    }

    public String toString() {
        return "chatFull#2e02a614";
    }
}
