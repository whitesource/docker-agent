/**
 * Copyright (C) 2016 WhiteSource Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.whitesource.docker;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.text.MessageFormat;
import java.util.Arrays;
import java.util.List;

/**
 * Displays progress of file extraction.
 *
 * @author tom.shapira
 */
public class ExtractProgressIndicator implements Runnable {

    /* --- Static members --- */

    public static final String LOG_PREFIX = "[INFO] ";
    public static final String PROGRESS_BAR_PREFIX = " [";
    public static final String PROGRESS_FILL = "#";
    public static final String EMPTY_STRING = " ";
    public static final String PROGRESS_BAR_SUFFIX = "] {0}% - {1}                       \r";
    public static final String CLEAR_PROGRESS = "                                                                                                                \r";
    public static final int REFRESH_RATE = 60;

    /* --- Members --- */

    private final List<String> progressAnimation = Arrays.asList("|", "/", "-", "\\");
    private final int ANIMATION_FRAMES = progressAnimation.size();
    private int animationIndex = 0;

    private final File file;
    private final long targetSize;
    private boolean finished;

    /* --- Constructors --- */

    public ExtractProgressIndicator(File file, long targetSize) {
        finished = false;
        this.file = file;
        this.targetSize = targetSize;
    }

    /* --- Overridden methods --- */

    @Override
    public void run() {
        long currentSize;
        do {
            currentSize = file.length();

            StringBuilder sb = new StringBuilder(LOG_PREFIX);
            int actualAnimationIndex = animationIndex % (ANIMATION_FRAMES);
            sb.append(progressAnimation.get((actualAnimationIndex) % ANIMATION_FRAMES));
            animationIndex++;

            // draw progress bar
            sb.append(PROGRESS_BAR_PREFIX);
            double percentage = ((double) currentSize / targetSize) * 100;
            int progressionBlocks = (int) (percentage / 3);
            for (int i = 0; i < progressionBlocks; i++) {
                sb.append(PROGRESS_FILL);
            }
            for (int i = progressionBlocks; i < 33; i++) {
                sb.append(EMPTY_STRING);
            }
            sb.append(PROGRESS_BAR_SUFFIX);
            System.out.print(MessageFormat.format(sb.toString(), (int) percentage, FileUtils.byteCountToDisplaySize(currentSize)));

            try {
                Thread.sleep(REFRESH_RATE);
            } catch (InterruptedException e) {
                // ignore
            }

            // check if finished, sometimes actual size might be larger than target size
            if (finished) {
                break;
            }
        } while (currentSize < targetSize);

        // clear progress animation
        System.out.print(CLEAR_PROGRESS);
    }

    public void finished() {
        finished = true;
    }
}