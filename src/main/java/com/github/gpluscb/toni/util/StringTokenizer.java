package com.github.gpluscb.toni.util;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@SuppressWarnings("ClassCanBeRecord")
public class StringTokenizer {
    @Nonnull
    private final char[] quoteChars;

    // TODO: (low prio) Support multi-char quote chars
    public StringTokenizer(@Nonnull char... quoteChars) {
        this.quoteChars = quoteChars;
    }

    @Nonnull
    public TokenList tokenize(@Nonnull String string) {
        List<TokenList.TokenInfo> tokensInfo = new ArrayList<>();

        char[] chars = string.toCharArray();

        // Null if currently not at token
        @Nullable Integer currTokenStartPosition = null;
        // Null if token has no quote char
        @Nullable Character currTokenQuoteChar = null;
        for (int i = 0; i < chars.length; i++) {
            char currChar = chars[i];

            if (currTokenStartPosition == null) {
                // If not in token right now, and space - ignore
                if (currChar == ' ' || currChar == '\n') continue;

                if (isQuoteChar(currChar)) {
                    currTokenQuoteChar = currChar;
                    // So we are in token next iteration. Leading spaces should not be ignored in quote.
                    currTokenStartPosition = i + 1;
                } else {
                    // currChar is definitely a token starting char
                    currTokenStartPosition = i;
                    currTokenQuoteChar = null;
                }
            } else {
                // We are in token
                if (currTokenQuoteChar == null) {
                    if (currChar == ' ' || currChar == '\n') {
                        // End token here
                        TokenList.TokenInfo currToken = new TokenList.TokenInfo(currTokenStartPosition, i, 0);
                        currTokenStartPosition = null;
                        tokensInfo.add(currToken);
                    }

                    // If not space, we are still in token
                } else {
                    if (currChar == currTokenQuoteChar) {
                        // End of quote - end token here
                        TokenList.TokenInfo currToken = new TokenList.TokenInfo(currTokenStartPosition, i, 1);
                        currTokenStartPosition = null;
                        currTokenQuoteChar = null;
                        tokensInfo.add(currToken);
                    }

                    // If not quote char, we are still in token, and still in quote
                }
            }
        }

        if (currTokenStartPosition != null) {
            // End of string and we still have an open token! Better close the token, regardless of if the quote was open.
            TokenList.TokenInfo lastToken = new TokenList.TokenInfo(currTokenStartPosition, chars.length, 0);
            tokensInfo.add(lastToken);
        }

        return new TokenList(string, tokensInfo);
    }

    private boolean isQuoteChar(char c) {
        for (char quoteChar : quoteChars)
            if (quoteChar == c) return true;
        return false;
    }

    public static class TokenList {
        @Nonnull
        private final String originalString;
        /**
         * Pairs are T:startPos - U:endPos
         */
        @Nonnull
        private final List<TokenInfo> tokensInfo;

        public TokenList(@Nonnull String originalString, @Nonnull List<TokenInfo> tokensInfo) {
            this.originalString = originalString;
            this.tokensInfo = tokensInfo;
        }

        @Nonnull
        public String getOriginalString() {
            return originalString;
        }

        public int getTokenNum() {
            return tokensInfo.size();
        }

        @Nonnull
        public List<TokenInfo> getTokensInfo() {
            return tokensInfo;
        }

        @Nonnull
        public List<String> getTokens() {
            return tokensInfo.stream().map(tokenInfo -> originalString.substring(tokenInfo.getStartPos(), tokenInfo.getEndPos())).toList();
        }

        @Nonnull
        public String getToken(int index) {
            TokenInfo tokenInfo = tokensInfo.get(index);
            return originalString.substring(tokenInfo.getStartPos(), tokenInfo.getEndPos());
        }

        // TODO: Slightly weird-ish quote behaviour on this and getTokensFrom

        /**
         * @param end exclusive
         */
        @Nonnull
        public String getTokenRange(int start, int end) {
            int startPos = tokensInfo.get(start).getStartPos();
            int endPos = tokensInfo.get(end - 1).getEndPos();
            return originalString.substring(startPos, endPos);
        }

        @Nonnull
        public String getTokensFrom(int start) {
            int startPos = tokensInfo.get(start).getStartPos();
            int endPos = tokensInfo.get(tokensInfo.size() - 1).getEndPos();
            return originalString.substring(startPos, endPos);
        }

        @Override
        public String toString() {
            return "TokenList{" +
                    "originalString='" + originalString + '\'' +
                    ", tokenPositions=" + tokensInfo +
                    ", tokens=" + getTokens() +
                    '}';
        }

        public static class TokenInfo {
            private final int startPos;
            private final int endPos;
            private final int quoteCharLength;

            public TokenInfo(int startPos, int endPos, int quoteCharLength) {
                this.startPos = startPos;
                this.endPos = endPos;
                this.quoteCharLength = quoteCharLength;
            }

            public int getStartPos() {
                return startPos;
            }

            public int getEndPos() {
                return endPos;
            }

            public int getQuoteCharLength() {
                return quoteCharLength;
            }
        }
    }
}
