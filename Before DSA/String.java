// Source code is decompiled from a .class file using FernFlower decompiler (from Intellij IDEA).
package java.lang;

import java.io.ObjectStreamField;
import java.io.Serializable;
import java.io.UnsupportedEncodingException;
import java.lang.constant.Constable;
import java.lang.constant.ConstantDesc;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandles;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CoderResult;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.IllegalCharsetNameException;
import java.nio.charset.MalformedInputException;
import java.nio.charset.UnmappableCharacterException;
import java.nio.charset.UnsupportedCharsetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Formatter;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Spliterator;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import jdk.internal.util.ArraysSupport;
import jdk.internal.util.Preconditions;
import jdk.internal.vm.annotation.ForceInline;
import jdk.internal.vm.annotation.IntrinsicCandidate;
import jdk.internal.vm.annotation.Stable;
import sun.nio.cs.ArrayDecoder;
import sun.nio.cs.ArrayEncoder;
import sun.nio.cs.ISO_8859_1;
import sun.nio.cs.US_ASCII;
import sun.nio.cs.UTF_8;

public final class String implements Serializable, Comparable<String>, CharSequence, Constable, ConstantDesc {
   @Stable
   private final byte[] value;
   private final byte coder;
   @Stable
   private int hash;
   private boolean hashIsZero;
   private static final long serialVersionUID = -6849794470754667710L;
   static final boolean COMPACT_STRINGS = true;
   private static final ObjectStreamField[] serialPersistentFields = new ObjectStreamField[0];
   private static final char REPL = '�';
   public static final Comparator<String> CASE_INSENSITIVE_ORDER = new CaseInsensitiveComparator();
   public static final Comparator<String> UNICODE_CASEFOLD_ORDER = new FoldCaseComparator();
   static final byte LATIN1 = 0;
   static final byte UTF16 = 1;

   public String() {
      this.value = "".value;
      this.coder = "".coder;
   }

   @IntrinsicCandidate
   public String(String original) {
      this.value = original.value;
      this.coder = original.coder;
      this.hash = original.hash;
      this.hashIsZero = original.hashIsZero;
   }

   public String(char[] value) {
      this((char[])value, 0, value.length, (Void)null);
   }

   public String(char[] value, int offset, int count) {
      this(value, offset, count, rangeCheck(value, offset, count));
   }

   private static Void rangeCheck(char[] value, int offset, int count) {
      checkBoundsOffCount(offset, count, value.length);
      return null;
   }

   public String(int[] codePoints, int offset, int count) {
      checkBoundsOffCount(offset, count, codePoints.length);
      if (count == 0) {
         this.value = "".value;
         this.coder = "".coder;
      } else if (COMPACT_STRINGS) {
         byte[] val = StringUTF16.compress(codePoints, offset, count);
         this.coder = StringUTF16.coderFromArrayLen(val, count);
         this.value = val;
      } else {
         this.coder = 1;
         this.value = StringUTF16.toBytes(codePoints, offset, count);
      }
   }

   /** @deprecated */
   @Deprecated(
      since = "1.1"
   )
   public String(byte[] ascii, int hibyte, int offset, int count) {
      checkBoundsOffCount(offset, count, ascii.length);
      if (count == 0) {
         this.value = "".value;
         this.coder = "".coder;
      } else {
         if (COMPACT_STRINGS && (byte)hibyte == 0) {
            this.value = Arrays.copyOfRange(ascii, offset, offset + count);
            this.coder = 0;
         } else {
            hibyte <<= 8;
            byte[] val = StringUTF16.newBytesFor(count);

            for(int i = 0; i < count; ++i) {
               StringUTF16.putChar(val, i, hibyte | ascii[offset++] & 255);
            }

            this.value = val;
            this.coder = 1;
         }

      }
   }

   /** @deprecated */
   @Deprecated(
      since = "1.1"
   )
   public String(byte[] ascii, int hibyte) {
      this(ascii, hibyte, 0, ascii.length);
   }

   public String(byte[] bytes, int offset, int length, String charsetName) throws UnsupportedEncodingException {
      this(lookupCharset(charsetName), bytes, checkBoundsOffCount(offset, length, bytes.length), length);
   }

   public String(byte[] bytes, int offset, int length, Charset charset) {
      this((Charset)Objects.requireNonNull(charset), bytes, checkBoundsOffCount(offset, length, bytes.length), length);
   }

   private String(Charset charset, byte[] bytes, int offset, int length) {
      String str;
      if (length == 0) {
         str = "";
      } else if (charset == UTF_8.INSTANCE) {
         str = utf8(bytes, offset, length);
      } else if (charset == ISO_8859_1.INSTANCE) {
         str = iso88591(bytes, offset, length);
      } else if (charset == US_ASCII.INSTANCE) {
         str = ascii(bytes, offset, length);
      } else {
         str = decode(charset, bytes, offset, length);
      }

      this(str);
   }

   private static String utf8(byte[] bytes, int offset, int length) {
      if (!COMPACT_STRINGS) {
         byte[] dst = StringUTF16.newBytesFor(length);
         int dp = decodeUTF8_UTF16(bytes, offset, offset + length, dst, 0);
         if (dp != length) {
            dst = Arrays.copyOf(dst, dp << 1);
         }

         return new String(dst, (byte)1);
      } else {
         int dp = StringCoding.countPositives(bytes, offset, length);
         if (dp == length) {
            return new String(Arrays.copyOfRange(bytes, offset, offset + length), (byte)0);
         } else {
            byte[] latin1 = Arrays.copyOfRange(bytes, offset, offset + length);
            int sp = dp;

            while(sp < length) {
               int b1 = latin1[sp++];
               if (b1 >= 0) {
                  latin1[dp++] = (byte)b1;
               } else {
                  if ((b1 & 254) == 194 && sp < length) {
                     int b2 = latin1[sp];
                     if (b2 < -64) {
                        latin1[dp++] = (byte)decode2(b1, b2);
                        ++sp;
                        continue;
                     }
                  }

                  --sp;
                  break;
               }
            }

            if (sp == length) {
               if (dp != latin1.length) {
                  latin1 = Arrays.copyOf(latin1, dp);
               }

               return new String(latin1, (byte)0);
            } else {
               byte[] utf16 = StringUTF16.newBytesFor(length);
               StringLatin1.inflate(latin1, 0, utf16, 0, dp);
               dp = decodeUTF8_UTF16(latin1, sp, length, utf16, dp);
               if (dp != length) {
                  utf16 = Arrays.copyOf(utf16, dp << 1);
               }

               return new String(utf16, (byte)1);
            }
         }
      }
   }

   private static String iso88591(byte[] bytes, int offset, int length) {
      return COMPACT_STRINGS ? new String(Arrays.copyOfRange(bytes, offset, offset + length), (byte)0) : new String(StringLatin1.inflate(bytes, offset, length), (byte)1);
   }

   private static String ascii(byte[] bytes, int offset, int length) {
      if (COMPACT_STRINGS && !StringCoding.hasNegatives(bytes, offset, length)) {
         return new String(Arrays.copyOfRange(bytes, offset, offset + length), (byte)0);
      } else {
         byte[] dst = StringUTF16.newBytesFor(length);
         int dp = 0;

         while(dp < length) {
            int b = bytes[offset++];
            StringUTF16.putChar(dst, dp++, b >= 0 ? (char)b : '�');
         }

         return new String(dst, (byte)1);
      }
   }

   private static String decode(Charset charset, byte[] bytes, int offset, int length) {
      CharsetDecoder cd = charset.newDecoder();
      if (cd instanceof ArrayDecoder ad) {
         if (ad.isASCIICompatible() && !StringCoding.hasNegatives(bytes, offset, length)) {
            return iso88591(bytes, offset, length);
         } else if (COMPACT_STRINGS && ad.isLatin1Decodable()) {
            byte[] dst = new byte[length];
            ad.decodeToLatin1(bytes, offset, length, dst);
            return new String(dst, (byte)0);
         } else {
            int en = scale(length, cd.maxCharsPerByte());
            cd.onMalformedInput(CodingErrorAction.REPLACE).onUnmappableCharacter(CodingErrorAction.REPLACE);
            char[] ca = new char[en];
            int clen = ad.decode(bytes, offset, length, ca);
            return new String(ca, 0, clen, (Void)null);
         }
      } else {
         int en = scale(length, cd.maxCharsPerByte());
         cd.onMalformedInput(CodingErrorAction.REPLACE).onUnmappableCharacter(CodingErrorAction.REPLACE);
         char[] ca = new char[en];

         int caLen;
         try {
            caLen = decodeWithDecoder(cd, ca, bytes, offset, length);
         } catch (CharacterCodingException x) {
            throw new Error(x);
         }

         return new String(ca, 0, caLen, (Void)null);
      }
   }

   private static String newStringUTF8OrThrow(byte[] bytes, int offset, int length) throws CharacterCodingException {
      checkBoundsOffCount(offset, length, bytes.length);
      if (length == 0) {
         return "";
      } else {
         int dp;
         byte[] dst;
         if (COMPACT_STRINGS) {
            dp = StringCoding.countPositives(bytes, offset, length);
            int sl = offset + length;
            if (dp == length) {
               if (length != bytes.length) {
                  return new String(Arrays.copyOfRange(bytes, offset, offset + length), (byte)0);
               }

               return new String(bytes, (byte)0);
            }

            dst = new byte[length];
            System.arraycopy(bytes, offset, dst, 0, dp);
            offset += dp;

            label61: {
               while(true) {
                  if (offset >= sl) {
                     break label61;
                  }

                  int b1 = bytes[offset++];
                  if (b1 < 0) {
                     if ((b1 & 254) != 194 || offset >= sl) {
                        break;
                     }

                     int b2 = bytes[offset];
                     if (b2 >= -64) {
                        break;
                     }

                     dst[dp++] = (byte)decode2(b1, b2);
                     ++offset;
                  } else {
                     dst[dp++] = (byte)b1;
                  }
               }

               --offset;
            }

            if (offset == sl) {
               if (dp != dst.length) {
                  dst = Arrays.copyOf(dst, dp);
               }

               return new String(dst, (byte)0);
            }

            if (dp == 0) {
               dst = StringUTF16.newBytesFor(length);
            } else {
               byte[] buf = StringUTF16.newBytesFor(length);
               StringLatin1.inflate(dst, 0, buf, 0, dp);
               dst = buf;
            }

            dp = decodeUTF8_UTF16OrThrow(bytes, offset, sl, dst, dp);
         } else {
            dst = StringUTF16.newBytesFor(length);
            dp = decodeUTF8_UTF16OrThrow(bytes, offset, offset + length, dst, 0);
         }

         if (dp != length) {
            dst = Arrays.copyOf(dst, dp << 1);
         }

         return new String(dst, (byte)1);
      }
   }

   static String newStringWithLatin1Bytes(byte[] src) {
      int len = src.length;
      if (len == 0) {
         return "";
      } else {
         return COMPACT_STRINGS ? new String(src, (byte)0) : new String(StringLatin1.inflate(src, 0, src.length), (byte)1);
      }
   }

   static String newStringOrThrow(byte[] src, Charset cs) throws CharacterCodingException {
      Objects.requireNonNull(cs);
      int len = src.length;
      if (len == 0) {
         return "";
      } else if (cs == UTF_8.INSTANCE) {
         return newStringUTF8OrThrow(src, 0, src.length);
      } else if (cs == ISO_8859_1.INSTANCE) {
         return COMPACT_STRINGS ? new String(src, (byte)0) : new String(StringLatin1.inflate(src, 0, src.length), (byte)1);
      } else if (cs == US_ASCII.INSTANCE) {
         if (!StringCoding.hasNegatives(src, 0, src.length)) {
            return COMPACT_STRINGS ? new String(src, (byte)0) : new String(StringLatin1.inflate(src, 0, src.length), (byte)1);
         } else {
            throw malformedASCII(src);
         }
      } else {
         CharsetDecoder cd = cs.newDecoder();
         if (cd instanceof ArrayDecoder) {
            ArrayDecoder ad = (ArrayDecoder)cd;
            if (ad.isASCIICompatible() && !StringCoding.hasNegatives(src, 0, src.length)) {
               if (COMPACT_STRINGS) {
                  return new String(src, (byte)0);
               }

               return new String(src, 0, src.length, ISO_8859_1.INSTANCE);
            }
         }

         int en = scale(len, cd.maxCharsPerByte());
         char[] ca = new char[en];
         int caLen = decodeWithDecoder(cd, ca, src, 0, src.length);
         if (COMPACT_STRINGS) {
            byte[] val = StringUTF16.compress(ca, 0, caLen);
            byte coder = StringUTF16.coderFromArrayLen(val, caLen);
            return new String(val, coder);
         } else {
            return new String(StringUTF16.toBytes(ca, 0, caLen), (byte)1);
         }
      }
   }

   private static byte[] trimArray(byte[] ba, int len) {
      return len == ba.length ? ba : Arrays.copyOf(ba, len);
   }

   private static int scale(int len, float expansionFactor) {
      return (int)((double)len * (double)expansionFactor);
   }

   private static Charset lookupCharset(String csn) throws UnsupportedEncodingException {
      Objects.requireNonNull(csn);

      try {
         return Charset.forName(csn);
      } catch (IllegalCharsetNameException | UnsupportedCharsetException var2) {
         throw new UnsupportedEncodingException(csn);
      }
   }

   private static byte[] encode(Charset cs, byte coder, byte[] val) {
      if (cs == UTF_8.INSTANCE) {
         return encodeUTF8(coder, val);
      } else if (cs == ISO_8859_1.INSTANCE) {
         return encode8859_1(coder, val);
      } else {
         return cs == US_ASCII.INSTANCE ? encodeASCII(coder, val) : encodeWithEncoder(cs, coder, val, (Class)null);
      }
   }

   private static <E extends Exception> byte[] encodeWithEncoder(Charset cs, byte coder, byte[] val, Class<E> exClass) throws E {
      CharsetEncoder ce = cs.newEncoder();
      int len = val.length >> coder;
      int en = scale(len, ce.maxBytesPerChar());
      boolean doReplace = exClass == null;
      if (doReplace && ce instanceof ArrayEncoder ae) {
         if (coder == 0 && ae.isASCIICompatible() && !StringCoding.hasNegatives(val, 0, val.length)) {
            return (byte[])(([B)val).clone();
         } else {
            byte[] ba = new byte[en];
            if (len == 0) {
               return ba;
            } else {
               int blen = coder == 0 ? ae.encodeFromLatin1(val, 0, len, ba, 0) : ae.encodeFromUTF16(val, 0, len, ba, 0);
               return trimArray(ba, blen);
            }
         }
      } else {
         byte[] ba = new byte[en];
         if (len == 0) {
            return ba;
         } else {
            if (doReplace) {
               ce.onMalformedInput(CodingErrorAction.REPLACE).onUnmappableCharacter(CodingErrorAction.REPLACE);
            }

            char[] ca = coder == 0 ? StringLatin1.toChars(val) : StringUTF16.toChars(val);
            ByteBuffer bb = ByteBuffer.wrap(ba);
            CharBuffer cb = CharBuffer.wrap(ca, 0, len);

            try {
               CoderResult cr = ce.encode(cb, bb, true);
               if (!cr.isUnderflow()) {
                  cr.throwException();
               }

               cr = ce.flush(bb);
               if (!cr.isUnderflow()) {
                  cr.throwException();
               }
            } catch (CharacterCodingException x) {
               if (!doReplace) {
                  throw x;
               }

               throw new Error(x);
            }

            return trimArray(ba, bb.position());
         }
      }
   }

   static byte[] getBytesUTF8OrThrow(String s) throws CharacterCodingException {
      return encodeUTF8OrThrow(s.coder(), s.value());
   }

   private static boolean isASCII(byte[] src) {
      return !StringCoding.hasNegatives(src, 0, src.length);
   }

   static byte[] getBytesOrThrow(String s, Charset cs) throws CharacterCodingException {
      Objects.requireNonNull(cs);
      byte[] val = s.value();
      byte coder = s.coder();
      if (cs == UTF_8.INSTANCE) {
         return coder == 0 && isASCII(val) ? val : encodeUTF8OrThrow(coder, val);
      } else if (cs == ISO_8859_1.INSTANCE) {
         return coder == 0 ? val : encode8859_1OrThrow(coder, val);
      } else if (cs == US_ASCII.INSTANCE && coder == 0) {
         if (isASCII(val)) {
            return val;
         } else {
            throw unmappableASCII(val);
         }
      } else {
         return encodeWithEncoder(cs, coder, val, CharacterCodingException.class);
      }
   }

   private static byte[] encodeASCII(byte coder, byte[] val) {
      if (coder == 0) {
         int positives = StringCoding.countPositives(val, 0, val.length);
         byte[] dst = (byte[])(([B)val).clone();
         if (positives < dst.length) {
            replaceNegatives(dst, positives);
         }

         return dst;
      } else {
         int len = val.length >> 1;
         byte[] dst = new byte[len];
         int dp = 0;

         for(int i = 0; i < len; ++i) {
            char c = StringUTF16.getChar(val, i);
            if (c < 128) {
               dst[dp++] = (byte)c;
            } else {
               if (Character.isHighSurrogate(c) && i + 1 < len && Character.isLowSurrogate(StringUTF16.getChar(val, i + 1))) {
                  ++i;
               }

               dst[dp++] = 63;
            }
         }

         if (len == dp) {
            return dst;
         } else {
            return Arrays.copyOf(dst, dp);
         }
      }
   }

   private static void replaceNegatives(byte[] val, int fromIndex) {
      for(int i = fromIndex; i < val.length; ++i) {
         if (val[i] < 0) {
            val[i] = 63;
         }
      }

   }

   private static byte[] encode8859_1(byte coder, byte[] val) {
      return encode8859_1(coder, val, (Class)null);
   }

   private static byte[] encode8859_1OrThrow(byte coder, byte[] val) throws UnmappableCharacterException {
      return encode8859_1(coder, val, UnmappableCharacterException.class);
   }

   private static <E extends Exception> byte[] encode8859_1(byte coder, byte[] val, Class<E> exClass) throws E {
      if (coder == 0) {
         return (byte[])(([B)val).clone();
      } else {
         int len = val.length >> 1;
         byte[] dst = new byte[len];
         int dp = 0;
         int sp = 0;
         int sl = len;

         while(sp < sl) {
            int ret = StringCoding.implEncodeISOArray(val, sp, dst, dp, len);
            sp += ret;
            dp += ret;
            if (ret != len) {
               if (exClass != null) {
                  throw unmappableCharacterException(sp);
               }

               char c = StringUTF16.getChar(val, sp++);
               if (Character.isHighSurrogate(c) && sp < sl && Character.isLowSurrogate(StringUTF16.getChar(val, sp))) {
                  ++sp;
               }

               dst[dp++] = 63;
               len = sl - sp;
            }
         }

         if (dp == dst.length) {
            return dst;
         } else {
            return Arrays.copyOf(dst, dp);
         }
      }
   }

   static int decodeASCII(byte[] sa, int sp, char[] da, int dp, int len) {
      int count;
      for(count = StringCoding.countPositives(sa, sp, len); count < len && sa[sp + count] >= 0; ++count) {
      }

      StringLatin1.inflate(sa, sp, da, dp, count);
      return count;
   }

   private static boolean isNotContinuation(int b) {
      return (b & 192) != 128;
   }

   private static boolean isMalformed3(int b1, int b2, int b3) {
      return b1 == -32 && (b2 & 224) == 128 || (b2 & 192) != 128 || (b3 & 192) != 128;
   }

   private static boolean isMalformed3_2(int b1, int b2) {
      return b1 == -32 && (b2 & 224) == 128 || (b2 & 192) != 128;
   }

   private static boolean isMalformed4(int b2, int b3, int b4) {
      return (b2 & 192) != 128 || (b3 & 192) != 128 || (b4 & 192) != 128;
   }

   private static boolean isMalformed4_2(int b1, int b2) {
      return b1 == 240 && (b2 < 144 || b2 > 191) || b1 == 244 && (b2 & 240) != 128 || (b2 & 192) != 128;
   }

   private static boolean isMalformed4_3(int b3) {
      return (b3 & 192) != 128;
   }

   private static char decode2(int b1, int b2) {
      return (char)(b1 << 6 ^ b2 ^ 3968);
   }

   private static char decode3(int b1, int b2, int b3) {
      return (char)(b1 << 12 ^ b2 << 6 ^ b3 ^ -123008);
   }

   private static int decode4(int b1, int b2, int b3, int b4) {
      return b1 << 18 ^ b2 << 12 ^ b3 << 6 ^ b4 ^ 3678080;
   }

   private static int decodeUTF8_UTF16(byte[] src, int sp, int sl, byte[] dst, int dp) {
      return decodeUTF8_UTF16(src, sp, sl, dst, dp, (Class)null);
   }

   private static int decodeUTF8_UTF16OrThrow(byte[] src, int sp, int sl, byte[] dst, int dp) throws MalformedInputException {
      return decodeUTF8_UTF16(src, sp, sl, dst, dp, MalformedInputException.class);
   }

   private static <E extends Exception> int decodeUTF8_UTF16(byte[] src, int sp, int sl, byte[] dst, int dp, Class<E> exClass) throws E {
      while(sp < sl) {
         int b1 = src[sp++];
         if (b1 >= 0) {
            StringUTF16.putChar(dst, dp++, (char)b1);
         } else if (b1 >> 5 == -2 && (b1 & 30) != 0) {
            if (sp < sl) {
               int b2 = src[sp++];
               if (isNotContinuation(b2)) {
                  if (exClass != null) {
                     throw malformedInputException(sp - 1, 1);
                  }

                  StringUTF16.putChar(dst, dp++, 65533);
                  --sp;
               } else {
                  StringUTF16.putChar(dst, dp++, decode2(b1, b2));
               }
            } else {
               if (exClass != null) {
                  throw malformedInputException(sp, 1);
               }

               StringUTF16.putChar(dst, dp++, 65533);
               break;
            }
         } else if (b1 >> 4 == -2) {
            if (sp + 1 < sl) {
               int b2 = src[sp++];
               int b3 = src[sp++];
               if (isMalformed3(b1, b2, b3)) {
                  if (exClass != null) {
                     throw malformedInputException(sp - 3, 3);
                  }

                  StringUTF16.putChar(dst, dp++, 65533);
                  sp -= 3;
                  sp += malformed3(src, sp);
               } else {
                  char c = decode3(b1, b2, b3);
                  if (Character.isSurrogate(c)) {
                     if (exClass != null) {
                        throw malformedInputException(sp - 3, 3);
                     }

                     StringUTF16.putChar(dst, dp++, 65533);
                  } else {
                     StringUTF16.putChar(dst, dp++, c);
                  }
               }
            } else if (sp < sl && isMalformed3_2(b1, src[sp])) {
               if (exClass != null) {
                  throw malformedInputException(sp - 1, 2);
               }

               StringUTF16.putChar(dst, dp++, 65533);
            } else {
               if (exClass != null) {
                  throw malformedInputException(sp, 1);
               }

               StringUTF16.putChar(dst, dp++, 65533);
               break;
            }
         } else if (b1 >> 3 == -2) {
            if (sp + 2 < sl) {
               int b2 = src[sp++];
               int b3 = src[sp++];
               int b4 = src[sp++];
               int uc = decode4(b1, b2, b3, b4);
               if (!isMalformed4(b2, b3, b4) && Character.isSupplementaryCodePoint(uc)) {
                  StringUTF16.putChar(dst, dp++, Character.highSurrogate(uc));
                  StringUTF16.putChar(dst, dp++, Character.lowSurrogate(uc));
               } else {
                  if (exClass != null) {
                     throw malformedInputException(sp - 4, 4);
                  }

                  StringUTF16.putChar(dst, dp++, 65533);
                  sp -= 4;
                  sp += malformed4(src, sp);
               }
            } else {
               b1 &= 255;
               if (b1 <= 244 && (sp >= sl || !isMalformed4_2(b1, src[sp] & 255))) {
                  if (exClass != null) {
                     throw malformedInputException(sp - 1, 1);
                  }

                  ++sp;
                  StringUTF16.putChar(dst, dp++, 65533);
                  if (sp >= sl || !isMalformed4_3(src[sp])) {
                     break;
                  }
               } else {
                  if (exClass != null) {
                     throw malformedInputException(sp - 1, 1);
                  }

                  StringUTF16.putChar(dst, dp++, 65533);
               }
            }
         } else {
            if (exClass != null) {
               throw malformedInputException(sp - 1, 1);
            }

            StringUTF16.putChar(dst, dp++, 65533);
         }
      }

      return dp;
   }

   private static int decodeWithDecoder(CharsetDecoder cd, char[] dst, byte[] src, int offset, int length) throws CharacterCodingException {
      ByteBuffer bb = ByteBuffer.wrap(src, offset, length);
      CharBuffer cb = CharBuffer.wrap(dst, 0, dst.length);
      CoderResult cr = cd.decode(bb, cb, true);
      if (!cr.isUnderflow()) {
         cr.throwException();
      }

      cr = cd.flush(cb);
      if (!cr.isUnderflow()) {
         cr.throwException();
      }

      return cb.position();
   }

   private static int malformed3(byte[] src, int sp) {
      int b1 = src[sp++];
      int b2 = src[sp];
      return (b1 != -32 || (b2 & 224) != 128) && !isNotContinuation(b2) ? 2 : 1;
   }

   private static int malformed4(byte[] src, int sp) {
      int b1 = src[sp++] & 255;
      int b2 = src[sp++] & 255;
      if (b1 <= 244 && (b1 != 240 || b2 >= 144 && b2 <= 191) && (b1 != 244 || (b2 & 240) == 128) && !isNotContinuation(b2)) {
         return isNotContinuation(src[sp]) ? 2 : 3;
      } else {
         return 1;
      }
   }

   private static <E extends Exception> E malformedInputException(int offset, int length) {
      MalformedInputException mie = new MalformedInputException(length);
      String msg = "malformed input offset : " + offset + ", length : " + length;
      mie.initCause(new IllegalArgumentException(msg));
      return (E)mie;
   }

   private static MalformedInputException malformedASCII(byte[] val) {
      int dp = StringCoding.countPositives(val, 0, val.length);
      return (MalformedInputException)malformedInputException(dp, 1);
   }

   private static <E extends Exception> E unmappableCharacterException(int offset) {
      UnmappableCharacterException uce = new UnmappableCharacterException(1);
      String msg = "malformed input offset : " + offset + ", length : 1";
      uce.initCause(new IllegalArgumentException(msg, uce));
      return (E)uce;
   }

   private static UnmappableCharacterException unmappableASCII(byte[] val) {
      int dp = StringCoding.countPositives(val, 0, val.length);
      return (UnmappableCharacterException)unmappableCharacterException(dp);
   }

   private static byte[] encodeUTF8(byte coder, byte[] val) {
      return encodeUTF8(coder, val, (Class)null);
   }

   private static byte[] encodeUTF8OrThrow(byte coder, byte[] val) throws UnmappableCharacterException {
      return encodeUTF8(coder, val, UnmappableCharacterException.class);
   }

   private static <E extends Exception> byte[] encodeUTF8(byte coder, byte[] val, Class<E> exClass) throws E {
      if (coder == 1) {
         return encodeUTF8_UTF16(val, exClass);
      } else {
         int positives = StringCoding.countPositives(val, 0, val.length);
         if (positives == val.length) {
            return (byte[])(([B)val).clone();
         } else {
            byte[] dst = StringUTF16.newBytesFor(val.length);
            if (positives > 0) {
               System.arraycopy(val, 0, dst, 0, positives);
            }

            int dp = positives;

            for(int i = positives; i < val.length; ++i) {
               byte c = val[i];
               if (c < 0) {
                  dst[dp++] = (byte)(192 | (c & 255) >> 6);
                  dst[dp++] = (byte)(128 | c & 63);
               } else {
                  dst[dp++] = c;
               }
            }

            if (dp == dst.length) {
               return dst;
            } else {
               return Arrays.copyOf(dst, dp);
            }
         }
      }
   }

   private static <E extends Exception> byte[] encodeUTF8_UTF16(byte[] val, Class<E> exClass) throws E {
      int dp = 0;
      int sp = 0;
      int sl = val.length >> 1;
      long allocLen = sl * 3 < 0 ? computeSizeUTF8_UTF16(val, exClass) : (long)(sl * 3);
      if (allocLen > 2147483647L) {
         throw new OutOfMemoryError("Required length exceeds implementation limit");
      } else {
         byte[] dst;
         for(dst = new byte[(int)allocLen]; sp < sl; ++sp) {
            char c = StringUTF16.getChar(val, sp);
            if (c >= 128) {
               break;
            }

            dst[dp++] = (byte)c;
         }

         while(sp < sl) {
            char c = StringUTF16.getChar(val, sp++);
            if (c < 128) {
               dst[dp++] = (byte)c;
            } else if (c < 2048) {
               dst[dp++] = (byte)(192 | c >> 6);
               dst[dp++] = (byte)(128 | c & 63);
            } else if (Character.isSurrogate(c)) {
               int uc = -1;
               char c2;
               if (Character.isHighSurrogate(c) && sp < sl && Character.isLowSurrogate(c2 = StringUTF16.getChar(val, sp))) {
                  uc = Character.toCodePoint(c, c2);
               }

               if (uc < 0) {
                  if (exClass != null) {
                     throw unmappableCharacterException(sp - 1);
                  }

                  dst[dp++] = 63;
               } else {
                  dst[dp++] = (byte)(240 | uc >> 18);
                  dst[dp++] = (byte)(128 | uc >> 12 & 63);
                  dst[dp++] = (byte)(128 | uc >> 6 & 63);
                  dst[dp++] = (byte)(128 | uc & 63);
                  ++sp;
               }
            } else {
               dst[dp++] = (byte)(224 | c >> 12);
               dst[dp++] = (byte)(128 | c >> 6 & 63);
               dst[dp++] = (byte)(128 | c & 63);
            }
         }

         if (dp == dst.length) {
            return dst;
         } else {
            return Arrays.copyOf(dst, dp);
         }
      }
   }

   private static <E extends Exception> long computeSizeUTF8_UTF16(byte[] val, Class<E> exClass) throws E {
      long dp = 0L;
      int sp = 0;
      int sl = val.length >> 1;

      while(sp < sl) {
         char c = StringUTF16.getChar(val, sp++);
         if (c < 128) {
            ++dp;
         } else if (c < 2048) {
            dp += 2L;
         } else if (Character.isSurrogate(c)) {
            int uc = -1;
            char c2;
            if (Character.isHighSurrogate(c) && sp < sl && Character.isLowSurrogate(c2 = StringUTF16.getChar(val, sp))) {
               uc = Character.toCodePoint(c, c2);
            }

            if (uc < 0) {
               if (exClass != null) {
                  throw unmappableCharacterException(sp - 1);
               }

               ++dp;
            } else {
               dp += 4L;
               ++sp;
            }
         } else {
            dp += 3L;
         }
      }

      return dp;
   }

   public String(byte[] bytes, String charsetName) throws UnsupportedEncodingException {
      this(lookupCharset(charsetName), bytes, 0, bytes.length);
   }

   public String(byte[] bytes, Charset charset) {
      this((Charset)Objects.requireNonNull(charset), bytes, 0, bytes.length);
   }

   public String(byte[] bytes, int offset, int length) {
      this(Charset.defaultCharset(), bytes, checkBoundsOffCount(offset, length, bytes.length), length);
   }

   public String(byte[] bytes) {
      this(Charset.defaultCharset(), bytes, 0, bytes.length);
   }

   public String(StringBuffer buffer) {
      this(buffer.toString());
   }

   public String(StringBuilder builder) {
      this((AbstractStringBuilder)builder, (Void)null);
   }

   public int length() {
      return this.value.length >> this.coder();
   }

   public boolean isEmpty() {
      return this.value.length == 0;
   }

   public char charAt(int index) {
      return this.isLatin1() ? StringLatin1.charAt(this.value, index) : StringUTF16.charAt(this.value, index);
   }

   public int codePointAt(int index) {
      if (this.isLatin1()) {
         checkIndex(index, this.value.length);
         return this.value[index] & 255;
      } else {
         int length = this.value.length >> 1;
         checkIndex(index, length);
         return StringUTF16.codePointAt(this.value, index, length);
      }
   }

   public int codePointBefore(int index) {
      int i = index - 1;
      checkIndex(i, this.length());
      return this.isLatin1() ? this.value[i] & 255 : StringUTF16.codePointBefore(this.value, index);
   }

   public int codePointCount(int beginIndex, int endIndex) {
      Objects.checkFromToIndex(beginIndex, endIndex, this.length());
      return this.isLatin1() ? endIndex - beginIndex : StringUTF16.codePointCount(this.value, beginIndex, endIndex);
   }

   public int offsetByCodePoints(int index, int codePointOffset) {
      return Character.offsetByCodePoints(this, index, codePointOffset);
   }

   public void getChars(int srcBegin, int srcEnd, char[] dst, int dstBegin) {
      checkBoundsBeginEnd(srcBegin, srcEnd, this.length());
      checkBoundsOffCount(dstBegin, srcEnd - srcBegin, dst.length);
      if (this.isLatin1()) {
         StringLatin1.getChars(this.value, srcBegin, srcEnd, dst, dstBegin);
      } else {
         StringUTF16.getChars(this.value, srcBegin, srcEnd, dst, dstBegin);
      }

   }

   /** @deprecated */
   @Deprecated(
      since = "1.1"
   )
   public void getBytes(int srcBegin, int srcEnd, byte[] dst, int dstBegin) {
      checkBoundsBeginEnd(srcBegin, srcEnd, this.length());
      Objects.requireNonNull(dst);
      checkBoundsOffCount(dstBegin, srcEnd - srcBegin, dst.length);
      if (this.isLatin1()) {
         StringLatin1.getBytes(this.value, srcBegin, srcEnd, dst, dstBegin);
      } else {
         StringUTF16.getBytes(this.value, srcBegin, srcEnd, dst, dstBegin);
      }

   }

   public byte[] getBytes(String charsetName) throws UnsupportedEncodingException {
      return encode(lookupCharset(charsetName), this.coder(), this.value);
   }

   public byte[] getBytes(Charset charset) {
      if (charset == null) {
         throw new NullPointerException();
      } else {
         return encode(charset, this.coder(), this.value);
      }
   }

   public byte[] getBytes() {
      return encode(Charset.defaultCharset(), this.coder(), this.value);
   }

   boolean bytesCompatible(Charset charset) {
      if (this.isLatin1()) {
         if (charset == ISO_8859_1.INSTANCE) {
            return true;
         }

         if (charset == UTF_8.INSTANCE || charset == US_ASCII.INSTANCE) {
            return !StringCoding.hasNegatives(this.value, 0, this.value.length);
         }
      }

      return false;
   }

   void copyToSegmentRaw(MemorySegment segment, long offset) {
      MemorySegment.copy(this.value, 0, segment, ValueLayout.JAVA_BYTE, offset, this.value.length);
   }

   public boolean equals(Object anObject) {
      if (this == anObject) {
         return true;
      } else {
         boolean var10000;
         if (anObject instanceof String) {
            String aString = (String)anObject;
            if ((!COMPACT_STRINGS || this.coder == aString.coder) && StringLatin1.equals(this.value, aString.value)) {
               var10000 = true;
               return var10000;
            }
         }

         var10000 = false;
         return var10000;
      }
   }

   public boolean contentEquals(StringBuffer sb) {
      return this.contentEquals((CharSequence)sb);
   }

   private boolean nonSyncContentEquals(AbstractStringBuilder sb) {
      int len = this.length();
      if (len != sb.length()) {
         return false;
      } else {
         byte[] v1 = this.value;
         byte[] v2 = sb.getValue();
         byte coder = this.coder();
         if (coder != sb.getCoder()) {
            return coder != 0 ? false : StringUTF16.contentEquals(v1, v2, len);
         } else {
            return v1.length <= v2.length && ArraysSupport.mismatch(v1, v2, v1.length) < 0;
         }
      }
   }

   public boolean contentEquals(CharSequence cs) {
      if (cs instanceof AbstractStringBuilder) {
         if (cs instanceof StringBuffer) {
            synchronized(cs) {
               return this.nonSyncContentEquals((AbstractStringBuilder)cs);
            }
         } else {
            return this.nonSyncContentEquals((AbstractStringBuilder)cs);
         }
      } else if (cs instanceof String) {
         return this.equals(cs);
      } else {
         int n = cs.length();
         if (n != this.length()) {
            return false;
         } else {
            byte[] val = this.value;
            if (this.isLatin1()) {
               for(int i = 0; i < n; ++i) {
                  if ((val[i] & 255) != cs.charAt(i)) {
                     return false;
                  }
               }
            } else if (!StringUTF16.contentEquals(val, cs, n)) {
               return false;
            }

            return true;
         }
      }
   }

   public boolean equalsIgnoreCase(String anotherString) {
      return this == anotherString ? true : anotherString != null && anotherString.length() == this.length() && this.regionMatches(true, 0, anotherString, 0, this.length());
   }

   public boolean equalsFoldCase(String anotherString) {
      if (this == anotherString) {
         return true;
      } else if (anotherString == null) {
         return false;
      } else {
         return UNICODE_CASEFOLD_ORDER.compare(this, anotherString) == 0;
      }
   }

   public int compareTo(String anotherString) {
      byte[] v1 = this.value;
      byte[] v2 = anotherString.value;
      byte coder = this.coder();
      if (coder == anotherString.coder()) {
         return coder == 0 ? StringLatin1.compareTo(v1, v2) : StringUTF16.compareTo(v1, v2);
      } else {
         return coder == 0 ? StringLatin1.compareToUTF16(v1, v2) : StringUTF16.compareToLatin1(v1, v2);
      }
   }

   public int compareToIgnoreCase(String str) {
      return CASE_INSENSITIVE_ORDER.compare(this, str);
   }

   public int compareToFoldCase(String str) {
      return UNICODE_CASEFOLD_ORDER.compare(this, str);
   }

   public boolean regionMatches(int toffset, String other, int ooffset, int len) {
      if (ooffset >= 0 && toffset >= 0 && (long)toffset <= (long)this.length() - (long)len && (long)ooffset <= (long)other.length() - (long)len) {
         if (len <= 0) {
            return true;
         } else {
            byte[] tv = this.value;
            byte[] ov = other.value;
            byte coder = this.coder();
            if (coder == other.coder()) {
               if (coder == 1) {
                  toffset <<= 1;
                  ooffset <<= 1;
                  len <<= 1;
               }

               return ArraysSupport.mismatch(tv, toffset, ov, ooffset, len) < 0;
            } else {
               if (coder == 0) {
                  while(len-- > 0) {
                     if (StringLatin1.getChar(tv, toffset++) != StringUTF16.getChar(ov, ooffset++)) {
                        return false;
                     }
                  }
               } else {
                  while(len-- > 0) {
                     if (StringUTF16.getChar(tv, toffset++) != StringLatin1.getChar(ov, ooffset++)) {
                        return false;
                     }
                  }
               }

               return true;
            }
         }
      } else {
         return false;
      }
   }

   public boolean regionMatches(boolean ignoreCase, int toffset, String other, int ooffset, int len) {
      if (!ignoreCase) {
         return this.regionMatches(toffset, other, ooffset, len);
      } else if (ooffset >= 0 && toffset >= 0 && (long)toffset <= (long)this.length() - (long)len && (long)ooffset <= (long)other.length() - (long)len) {
         byte[] tv = this.value;
         byte[] ov = other.value;
         byte coder = this.coder();
         if (coder == other.coder()) {
            return coder == 0 ? StringLatin1.regionMatchesCI(tv, toffset, ov, ooffset, len) : StringUTF16.regionMatchesCI(tv, toffset, ov, ooffset, len);
         } else {
            return coder == 0 ? StringLatin1.regionMatchesCI_UTF16(tv, toffset, ov, ooffset, len) : StringUTF16.regionMatchesCI_Latin1(tv, toffset, ov, ooffset, len);
         }
      } else {
         return false;
      }
   }

   public boolean startsWith(String prefix, int toffset) {
      if (toffset >= 0 && toffset <= this.length() - prefix.length()) {
         byte[] ta = this.value;
         byte[] pa = prefix.value;
         int po = 0;
         int pc = pa.length;
         byte coder = this.coder();
         if (coder == prefix.coder()) {
            if (coder == 1) {
               toffset <<= 1;
            }

            return ArraysSupport.mismatch(ta, toffset, pa, 0, pc) < 0;
         } else if (coder == 0) {
            return false;
         } else {
            while(po < pc) {
               if (StringUTF16.getChar(ta, toffset++) != (pa[po++] & 255)) {
                  return false;
               }
            }

            return true;
         }
      } else {
         return false;
      }
   }

   public boolean startsWith(String prefix) {
      return this.startsWith(prefix, 0);
   }

   public boolean endsWith(String suffix) {
      return this.startsWith(suffix, this.length() - suffix.length());
   }

   public int hashCode() {
      int h = this.hash;
      if (h == 0 && !this.hashIsZero) {
         h = this.isLatin1() ? StringLatin1.hashCode(this.value) : StringUTF16.hashCode(this.value);
         if (h == 0) {
            this.hashIsZero = true;
         } else {
            this.hash = h;
         }
      }

      return h;
   }

   public int indexOf(int ch) {
      return this.isLatin1() ? StringLatin1.indexOf(this.value, ch, 0, this.value.length) : StringUTF16.indexOf(this.value, ch, 0, this.value.length >> 1);
   }

   public int indexOf(int ch, int fromIndex) {
      fromIndex = Math.max(fromIndex, 0);
      return this.isLatin1() ? StringLatin1.indexOf(this.value, ch, Math.min(fromIndex, this.value.length), this.value.length) : StringUTF16.indexOf(this.value, ch, Math.min(fromIndex, this.value.length >> 1), this.value.length >> 1);
   }

   public int indexOf(int ch, int beginIndex, int endIndex) {
      checkBoundsBeginEnd(beginIndex, endIndex, this.length());
      return this.isLatin1() ? StringLatin1.indexOf(this.value, ch, beginIndex, endIndex) : StringUTF16.indexOf(this.value, ch, beginIndex, endIndex);
   }

   public int lastIndexOf(int ch) {
      return this.lastIndexOf(ch, this.length() - 1);
   }

   public int lastIndexOf(int ch, int fromIndex) {
      return this.isLatin1() ? StringLatin1.lastIndexOf(this.value, ch, fromIndex) : StringUTF16.lastIndexOf(this.value, ch, fromIndex);
   }

   public int indexOf(String str) {
      byte coder = this.coder();
      if (coder == str.coder()) {
         return this.isLatin1() ? StringLatin1.indexOf(this.value, str.value) : StringUTF16.indexOf(this.value, str.value);
      } else {
         return coder == 0 ? -1 : StringUTF16.indexOfLatin1(this.value, str.value);
      }
   }

   public int indexOf(String str, int fromIndex) {
      return indexOf(this.value, this.coder(), this.length(), str, fromIndex);
   }

   public int indexOf(String str, int beginIndex, int endIndex) {
      if (str.length() == 1) {
         return this.indexOf(str.charAt(0), beginIndex, endIndex);
      } else {
         checkBoundsBeginEnd(beginIndex, endIndex, this.length());
         return indexOf(this.value, this.coder(), endIndex, str, beginIndex);
      }
   }

   static int indexOf(byte[] src, byte srcCoder, int srcCount, String tgtStr, int fromIndex) {
      fromIndex = Math.clamp((long)fromIndex, 0, srcCount);
      int tgtCount = tgtStr.length();
      if (tgtCount > srcCount - fromIndex) {
         return -1;
      } else if (tgtCount == 0) {
         return fromIndex;
      } else {
         byte[] tgt = tgtStr.value;
         byte tgtCoder = tgtStr.coder();
         if (srcCoder == tgtCoder) {
            return srcCoder == 0 ? StringLatin1.indexOf(src, srcCount, tgt, tgtCount, fromIndex) : StringUTF16.indexOf(src, srcCount, tgt, tgtCount, fromIndex);
         } else {
            return srcCoder == 0 ? -1 : StringUTF16.indexOfLatin1(src, srcCount, tgt, tgtCount, fromIndex);
         }
      }
   }

   public int lastIndexOf(String str) {
      return this.lastIndexOf(str, this.length());
   }

   public int lastIndexOf(String str, int fromIndex) {
      return lastIndexOf(this.value, this.coder(), this.length(), str, fromIndex);
   }

   static int lastIndexOf(byte[] src, byte srcCoder, int srcCount, String tgtStr, int fromIndex) {
      byte[] tgt = tgtStr.value;
      byte tgtCoder = tgtStr.coder();
      int tgtCount = tgtStr.length();
      int rightIndex = srcCount - tgtCount;
      if (fromIndex > rightIndex) {
         fromIndex = rightIndex;
      }

      if (fromIndex < 0) {
         return -1;
      } else if (tgtCount == 0) {
         return fromIndex;
      } else if (srcCoder == tgtCoder) {
         return srcCoder == 0 ? StringLatin1.lastIndexOf(src, srcCount, tgt, tgtCount, fromIndex) : StringUTF16.lastIndexOf(src, srcCount, tgt, tgtCount, fromIndex);
      } else {
         return srcCoder == 0 ? -1 : StringUTF16.lastIndexOfLatin1(src, srcCount, tgt, tgtCount, fromIndex);
      }
   }

   public String substring(int beginIndex) {
      return this.substring(beginIndex, this.length());
   }

   public String substring(int beginIndex, int endIndex) {
      int length = this.length();
      checkBoundsBeginEnd(beginIndex, endIndex, length);
      if (beginIndex == 0 && endIndex == length) {
         return this;
      } else {
         int subLen = endIndex - beginIndex;
         return this.isLatin1() ? StringLatin1.newString(this.value, beginIndex, subLen) : StringUTF16.newString(this.value, beginIndex, subLen);
      }
   }

   public CharSequence subSequence(int beginIndex, int endIndex) {
      return this.substring(beginIndex, endIndex);
   }

   public String concat(String str) {
      return str.isEmpty() ? this : StringConcatHelper.doConcat(this, str);
   }

   public String replace(char oldChar, char newChar) {
      if (oldChar != newChar) {
         String ret = this.isLatin1() ? StringLatin1.replace(this.value, oldChar, newChar) : StringUTF16.replace(this.value, oldChar, newChar);
         if (ret != null) {
            return ret;
         }
      }

      return this;
   }

   public boolean matches(String regex) {
      return Pattern.matches(regex, this);
   }

   public boolean contains(CharSequence s) {
      return this.indexOf(s.toString()) >= 0;
   }

   public String replaceFirst(String regex, String replacement) {
      return Pattern.compile(regex).matcher(this).replaceFirst(replacement);
   }

   public String replaceAll(String regex, String replacement) {
      return Pattern.compile(regex).matcher(this).replaceAll(replacement);
   }

   public String replace(CharSequence target, CharSequence replacement) {
      String trgtStr = target.toString();
      String replStr = replacement.toString();
      int thisLen = this.length();
      int trgtLen = trgtStr.length();
      int replLen = replStr.length();
      if (trgtLen > 0) {
         if (trgtLen == 1 && replLen == 1) {
            return this.replace(trgtStr.charAt(0), replStr.charAt(0));
         } else {
            boolean thisIsLatin1 = this.isLatin1();
            boolean trgtIsLatin1 = trgtStr.isLatin1();
            boolean replIsLatin1 = replStr.isLatin1();
            String ret = thisIsLatin1 && trgtIsLatin1 && replIsLatin1 ? StringLatin1.replace(this.value, thisLen, trgtStr.value, trgtLen, replStr.value, replLen) : StringUTF16.replace(this.value, thisLen, thisIsLatin1, trgtStr.value, trgtLen, trgtIsLatin1, replStr.value, replLen, replIsLatin1);
            return ret != null ? ret : this;
         }
      } else {
         int resultLen;
         try {
            resultLen = Math.addExact(thisLen, Math.multiplyExact(Math.addExact(thisLen, 1), replLen));
         } catch (ArithmeticException var12) {
            throw new OutOfMemoryError("Required length exceeds implementation limit");
         }

         StringBuilder sb = new StringBuilder(resultLen);
         sb.append(replStr);

         for(int i = 0; i < thisLen; ++i) {
            sb.append(this.charAt(i)).append(replStr);
         }

         return sb.toString();
      }
   }

   public String[] split(String regex, int limit) {
      return this.split(regex, limit, false);
   }

   public String[] splitWithDelimiters(String regex, int limit) {
      return this.split(regex, limit, true);
   }

   private String[] split(String regex, int limit, boolean withDelimiters) {
      char ch = 0;
      if ((regex.length() != 1 || ".$|()[{^?*+\\".indexOf(ch = regex.charAt(0)) != -1) && (regex.length() != 2 || regex.charAt(0) != '\\' || ((ch = regex.charAt(1)) - 48 | 57 - ch) >= 0 || (ch - 97 | 122 - ch) >= 0 || (ch - 65 | 90 - ch) >= 0) || ch >= '\ud800' && ch <= '\udfff') {
         Pattern pattern = Pattern.compile(regex);
         return withDelimiters ? pattern.splitWithDelimiters(this, limit) : pattern.split(this, limit);
      } else {
         return this.split(ch, limit, withDelimiters);
      }
   }

   private String[] split(char ch, int limit, boolean withDelimiters) {
      int matchCount = 0;
      int off = 0;
      boolean limited = limit > 0;
      ArrayList<String> list = new ArrayList();

      int next;
      for(String del = withDelimiters ? valueOf(ch) : null; (next = this.indexOf(ch, off)) != -1; ++matchCount) {
         if (limited && matchCount >= limit - 1) {
            int last = this.length();
            list.add(this.substring(off, last));
            off = last;
            ++matchCount;
            break;
         }

         list.add(this.substring(off, next));
         if (withDelimiters) {
            list.add(del);
         }

         off = next + 1;
      }

      if (off == 0) {
         return new String[]{this};
      } else {
         if (!limited || matchCount < limit) {
            list.add(this.substring(off, this.length()));
         }

         int resultSize = list.size();
         if (limit == 0) {
            while(resultSize > 0 && ((String)list.get(resultSize - 1)).isEmpty()) {
               --resultSize;
            }
         }

         String[] result = new String[resultSize];
         return (String[])list.subList(0, resultSize).toArray(result);
      }
   }

   public String[] split(String regex) {
      return this.split(regex, 0, false);
   }

   public static String join(CharSequence delimiter, CharSequence... elements) {
      String delim = delimiter.toString();
      String[] elems = new String[elements.length];

      for(int i = 0; i < elements.length; ++i) {
         elems[i] = valueOf((Object)elements[i]);
      }

      return join("", "", delim, elems, elems.length);
   }

   @ForceInline
   static String join(String prefix, String suffix, String delimiter, String[] elements, int size) {
      int icoder = prefix.coder() | suffix.coder();
      long len = (long)prefix.length() + (long)suffix.length();
      if (size > 1) {
         len += (long)(size - 1) * (long)delimiter.length();
         icoder |= delimiter.coder();
      }

      for(int i = 0; i < size; ++i) {
         String el = elements[i];
         len += (long)el.length();
         icoder |= el.coder();
      }

      byte coder = (byte)icoder;
      if (len >= 0L && (len = len << coder) == (long)((int)len)) {
         byte[] value = StringConcatHelper.newArray((int)len);
         int off = 0;
         prefix.getBytes(value, off, coder);
         off += prefix.length();
         if (size > 0) {
            String el = elements[0];
            el.getBytes(value, off, coder);
            off += el.length();

            for(int i = 1; i < size; ++i) {
               delimiter.getBytes(value, off, coder);
               off += delimiter.length();
               el = elements[i];
               el.getBytes(value, off, coder);
               off += el.length();
            }
         }

         suffix.getBytes(value, off, coder);
         return new String(value, coder);
      } else {
         throw new OutOfMemoryError("Requested string length exceeds VM limit");
      }
   }

   public static String join(CharSequence delimiter, Iterable<? extends CharSequence> elements) {
      Objects.requireNonNull(delimiter);
      Objects.requireNonNull(elements);
      String delim = delimiter.toString();
      String[] elems = new String[8];
      int size = 0;

      for(CharSequence cs : elements) {
         if (size >= elems.length) {
            elems = (String[])Arrays.copyOf(elems, elems.length << 1);
         }

         elems[size++] = valueOf((Object)cs);
      }

      return join("", "", delim, elems, size);
   }

   public String toLowerCase(Locale locale) {
      return this.isLatin1() ? StringLatin1.toLowerCase(this, this.value, locale) : StringUTF16.toLowerCase(this, this.value, locale);
   }

   public String toLowerCase() {
      return this.toLowerCase(Locale.getDefault());
   }

   public String toUpperCase(Locale locale) {
      return this.isLatin1() ? StringLatin1.toUpperCase(this, this.value, locale) : StringUTF16.toUpperCase(this, this.value, locale);
   }

   public String toUpperCase() {
      return this.toUpperCase(Locale.getDefault());
   }

   public String trim() {
      String ret = this.isLatin1() ? StringLatin1.trim(this.value) : StringUTF16.trim(this.value);
      return ret == null ? this : ret;
   }

   public String strip() {
      String ret = this.isLatin1() ? StringLatin1.strip(this.value) : StringUTF16.strip(this.value);
      return ret == null ? this : ret;
   }

   public String stripLeading() {
      String ret = this.isLatin1() ? StringLatin1.stripLeading(this.value) : StringUTF16.stripLeading(this.value);
      return ret == null ? this : ret;
   }

   public String stripTrailing() {
      String ret = this.isLatin1() ? StringLatin1.stripTrailing(this.value) : StringUTF16.stripTrailing(this.value);
      return ret == null ? this : ret;
   }

   public boolean isBlank() {
      return this.indexOfNonWhitespace() == this.length();
   }

   public Stream<String> lines() {
      return this.isLatin1() ? StringLatin1.lines(this.value) : StringUTF16.lines(this.value);
   }

   public String indent(int n) {
      if (this.isEmpty()) {
         return "";
      } else {
         Stream<String> stream = this.lines();
         if (n > 0) {
            String spaces = " ".repeat(n);
            stream = stream.map((s) -> spaces + s);
         } else if (n == Integer.MIN_VALUE) {
            stream = stream.map((s) -> s.stripLeading());
         } else if (n < 0) {
            stream = stream.map((s) -> s.substring(Math.min(-n, s.indexOfNonWhitespace())));
         }

         return (String)stream.collect(Collectors.joining("\n", "", "\n"));
      }
   }

   private int indexOfNonWhitespace() {
      return this.isLatin1() ? StringLatin1.indexOfNonWhitespace(this.value) : StringUTF16.indexOfNonWhitespace(this.value);
   }

   private int lastIndexOfNonWhitespace() {
      return this.isLatin1() ? StringLatin1.lastIndexOfNonWhitespace(this.value) : StringUTF16.lastIndexOfNonWhitespace(this.value);
   }

   public String stripIndent() {
      int length = this.length();
      if (length == 0) {
         return "";
      } else {
         char lastChar = this.charAt(length - 1);
         boolean optOut = lastChar == '\n' || lastChar == '\r';
         List<String> lines = this.lines().toList();
         int outdent = optOut ? 0 : outdent(lines);
         return (String)lines.stream().map((line) -> {
            int firstNonWhitespace = line.indexOfNonWhitespace();
            int lastNonWhitespace = line.lastIndexOfNonWhitespace();
            int incidentalWhitespace = Math.min(outdent, firstNonWhitespace);
            return firstNonWhitespace > lastNonWhitespace ? "" : line.substring(incidentalWhitespace, lastNonWhitespace);
         }).collect(Collectors.joining("\n", "", optOut ? "\n" : ""));
      }
   }

   private static int outdent(List<String> lines) {
      int outdent = Integer.MAX_VALUE;

      for(String line : lines) {
         int leadingWhitespace = line.indexOfNonWhitespace();
         if (leadingWhitespace != line.length()) {
            outdent = Integer.min(outdent, leadingWhitespace);
         }
      }

      String lastLine = (String)lines.get(lines.size() - 1);
      if (lastLine.isBlank()) {
         outdent = Integer.min(outdent, lastLine.length());
      }

      return outdent;
   }

   public String translateEscapes() {
      if (this.isEmpty()) {
         return "";
      } else {
         char[] chars = this.toCharArray();
         int length = chars.length;
         int from = 0;
         int to = 0;

         while(from < length) {
            char ch = chars[from++];
            if (ch == '\\') {
               ch = from < length ? chars[from++] : 0;
               switch (ch) {
                  case '\n':
                     continue;
                  case '\r':
                     if (from < length && chars[from] == '\n') {
                        ++from;
                     }
                     continue;
                  case '"':
                  case '\'':
                  case '\\':
                     break;
                  case '0':
                  case '1':
                  case '2':
                  case '3':
                  case '4':
                  case '5':
                  case '6':
                  case '7':
                     int limit = Integer.min(from + (ch <= '3' ? 2 : 1), length);

                     int code;
                     for(code = ch - 48; from < limit; code = code << 3 | ch - 48) {
                        ch = chars[from];
                        if (ch < '0' || '7' < ch) {
                           break;
                        }

                        ++from;
                     }

                     ch = (char)code;
                     break;
                  case 'b':
                     ch = '\b';
                     break;
                  case 'f':
                     ch = '\f';
                     break;
                  case 'n':
                     ch = '\n';
                     break;
                  case 'r':
                     ch = '\r';
                     break;
                  case 's':
                     ch = ' ';
                     break;
                  case 't':
                     ch = '\t';
                     break;
                  default:
                     String msg = format("Invalid escape sequence: \\%c \\\\u%04X", ch, Integer.valueOf(ch));
                     throw new IllegalArgumentException(msg);
               }
            }

            chars[to++] = ch;
         }

         return new String(chars, 0, to);
      }
   }

   public <R> R transform(Function<? super String, ? extends R> f) {
      return (R)f.apply(this);
   }

   public String toString() {
      return this;
   }

   public IntStream chars() {
      return StreamSupport.intStream((Spliterator.OfInt)(this.isLatin1() ? new StringLatin1.CharsSpliterator(this.value, 1024) : new StringUTF16.CharsSpliterator(this.value, 1024)), false);
   }

   public IntStream codePoints() {
      return StreamSupport.intStream((Spliterator.OfInt)(this.isLatin1() ? new StringLatin1.CharsSpliterator(this.value, 1024) : new StringUTF16.CodePointsSpliterator(this.value, 1024)), false);
   }

   public char[] toCharArray() {
      return this.isLatin1() ? StringLatin1.toChars(this.value) : StringUTF16.toChars(this.value);
   }

   public static String format(String format, Object... args) {
      return (new Formatter()).format(format, args).toString();
   }

   public static String format(Locale l, String format, Object... args) {
      return (new Formatter(l)).format(format, args).toString();
   }

   public String formatted(Object... args) {
      return (new Formatter()).format(this, args).toString();
   }

   public static String valueOf(Object obj) {
      return obj == null ? "null" : obj.toString();
   }

   public static String valueOf(char[] data) {
      return new String(data);
   }

   public static String valueOf(char[] data, int offset, int count) {
      return new String(data, offset, count);
   }

   public static String copyValueOf(char[] data, int offset, int count) {
      return new String(data, offset, count);
   }

   public static String copyValueOf(char[] data) {
      return new String(data);
   }

   public static String valueOf(boolean b) {
      return b ? "true" : "false";
   }

   public static String valueOf(char c) {
      return COMPACT_STRINGS && StringLatin1.canEncode(c) ? new String(StringLatin1.toBytes(c), (byte)0) : new String(StringUTF16.toBytes(c), (byte)1);
   }

   public static String valueOf(int i) {
      return Integer.toString(i);
   }

   public static String valueOf(long l) {
      return Long.toString(l);
   }

   public static String valueOf(float f) {
      return Float.toString(f);
   }

   public static String valueOf(double d) {
      return Double.toString(d);
   }

   public native String intern();

   public String repeat(int count) {
      if (count < 0) {
         throw new IllegalArgumentException("count is negative: " + count);
      } else if (count == 1) {
         return this;
      } else {
         int len = this.value.length;
         if (len != 0 && count != 0) {
            if (Integer.MAX_VALUE / count < len) {
               throw new OutOfMemoryError("Required length exceeds implementation limit");
            } else if (len == 1) {
               byte[] single = new byte[count];
               Arrays.fill(single, this.value[0]);
               return new String(single, this.coder);
            } else {
               int limit = len * count;
               byte[] multiple = new byte[limit];
               System.arraycopy(this.value, 0, multiple, 0, len);
               repeatCopyRest(multiple, 0, limit, len);
               return new String(multiple, this.coder);
            }
         } else {
            return "";
         }
      }
   }

   static void repeatCopyRest(byte[] buffer, int offset, int limit, int copied) {
      while(copied < limit - copied) {
         System.arraycopy(buffer, offset, buffer, offset + copied, copied);
         copied <<= 1;
      }

      System.arraycopy(buffer, offset, buffer, offset + copied, limit - copied);
   }

   void getBytes(byte[] dst, int dstBegin, byte coder) {
      if (this.coder() == coder) {
         System.arraycopy(this.value, 0, dst, dstBegin << coder, this.value.length);
      } else {
         StringLatin1.inflate(this.value, 0, dst, dstBegin, this.value.length);
      }

   }

   void getBytes(byte[] dst, int srcPos, int dstBegin, byte coder, int length) {
      if (this.coder() == coder) {
         System.arraycopy(this.value, srcPos << coder, dst, dstBegin << coder, length << coder);
      } else {
         StringLatin1.inflate(this.value, srcPos, dst, dstBegin, length);
      }

   }

   private String(char[] value, int off, int len, Void sig) {
      if (len == 0) {
         this.value = "".value;
         this.coder = "".coder;
      } else if (COMPACT_STRINGS) {
         byte[] val = StringUTF16.compress(value, off, len);
         this.coder = StringUTF16.coderFromArrayLen(val, len);
         this.value = val;
      } else {
         this.coder = 1;
         this.value = StringUTF16.toBytes(value, off, len);
      }
   }

   String(AbstractStringBuilder asb, Void sig) {
      byte[] val = asb.getValue();
      int length = asb.length();
      if (asb.isLatin1()) {
         this.coder = 0;
         this.value = Arrays.copyOfRange(val, 0, length);
      } else {
         if (COMPACT_STRINGS && asb.maybeLatin1) {
            this.value = StringUTF16.compress(val, 0, length);
            this.coder = StringUTF16.coderFromArrayLen(this.value, length);
            return;
         }

         this.coder = 1;
         this.value = Arrays.copyOfRange(val, 0, length << 1);
      }

   }

   String(byte[] value, byte coder) {
      this.value = value;
      this.coder = coder;
   }

   byte coder() {
      return COMPACT_STRINGS ? this.coder : 1;
   }

   byte[] value() {
      return this.value;
   }

   boolean isLatin1() {
      return COMPACT_STRINGS && this.coder == 0;
   }

   static void checkIndex(int index, int length) {
      Preconditions.checkIndex(index, length, Preconditions.SIOOBE_FORMATTER);
   }

   static void checkOffset(int offset, int length) {
      Preconditions.checkFromToIndex(offset, length, length, Preconditions.SIOOBE_FORMATTER);
   }

   static int checkBoundsOffCount(int offset, int count, int length) {
      return Preconditions.checkFromIndexSize(offset, count, length, Preconditions.SIOOBE_FORMATTER);
   }

   static void checkBoundsBeginEnd(int begin, int end, int length) {
      Preconditions.checkFromToIndex(begin, end, length, Preconditions.SIOOBE_FORMATTER);
   }

   static String valueOfCodePoint(int codePoint) {
      if (COMPACT_STRINGS && StringLatin1.canEncode(codePoint)) {
         return new String(StringLatin1.toBytes((char)codePoint), (byte)0);
      } else if (Character.isBmpCodePoint(codePoint)) {
         return new String(StringUTF16.toBytes((char)codePoint), (byte)1);
      } else if (Character.isSupplementaryCodePoint(codePoint)) {
         return new String(StringUTF16.toBytesSupplementary(codePoint), (byte)1);
      } else {
         throw new IllegalArgumentException(format("Not a valid Unicode code point: 0x%X", codePoint));
      }
   }

   public Optional<String> describeConstable() {
      return Optional.of(this);
   }

   public String resolveConstantDesc(MethodHandles.Lookup lookup) {
      return this;
   }

   private static class CaseInsensitiveComparator implements Comparator<String>, Serializable {
      private static final long serialVersionUID = 8575799808933029326L;

      private CaseInsensitiveComparator() {
      }

      public int compare(String s1, String s2) {
         byte[] v1 = s1.value;
         byte[] v2 = s2.value;
         byte coder = s1.coder();
         if (coder == s2.coder()) {
            return coder == 0 ? StringLatin1.compareToCI(v1, v2) : StringUTF16.compareToCI(v1, v2);
         } else {
            return coder == 0 ? StringLatin1.compareToCI_UTF16(v1, v2) : StringUTF16.compareToCI_Latin1(v1, v2);
         }
      }

      private Object readResolve() {
         return String.CASE_INSENSITIVE_ORDER;
      }
   }

   private static class FoldCaseComparator implements Comparator<String> {
      private FoldCaseComparator() {
      }

      public int compare(String s1, String s2) {
         byte[] v1 = s1.value;
         byte[] v2 = s2.value;
         if (s1.coder == s2.coder()) {
            return s1.coder == 0 ? StringLatin1.compareToFC(v1, v2) : StringUTF16.compareToFC(v1, v2);
         } else {
            return s1.coder == 0 ? StringLatin1.compareToFC_UTF16(v1, v2) : StringUTF16.compareToFC_Latin1(v1, v2);
         }
      }
   }
}
