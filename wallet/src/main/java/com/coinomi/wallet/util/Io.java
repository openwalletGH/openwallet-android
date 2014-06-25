package com.coinomi.wallet.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.lang.reflect.Method;

import javax.annotation.Nonnull;

/**
 * @author Andreas Schildbach
 */
public class Io
{
    private static final Logger log = LoggerFactory.getLogger(Io.class);

    public static final long copy(@Nonnull final Reader reader, @Nonnull final StringBuilder builder) throws IOException
    {
        return copy(reader, builder, 0);
    }

    public static final long copy(@Nonnull final Reader reader, @Nonnull final StringBuilder builder, final long maxChars) throws IOException
    {
        final char[] buffer = new char[256];
        long count = 0;
        int n = 0;
        while (-1 != (n = reader.read(buffer)))
        {
            builder.append(buffer, 0, n);
            count += n;

            if (maxChars != 0 && count > maxChars)
                throw new IOException("Read more than the limit of " + maxChars + " characters");
        }
        return count;
    }

    public static final long copy(@Nonnull final InputStream is, @Nonnull final OutputStream os) throws IOException
    {
        final byte[] buffer = new byte[1024];
        long count = 0;
        int n = 0;
        while (-1 != (n = is.read(buffer)))
        {
            os.write(buffer, 0, n);
            count += n;
        }
        return count;
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    public static void chmod(@Nonnull final File path, final int mode)
    {
        try
        {
            final Class fileUtils = Class.forName("android.os.FileUtils");
            final Method setPermissions = fileUtils.getMethod("setPermissions", String.class, int.class, int.class, int.class);
            setPermissions.invoke(null, path.getAbsolutePath(), mode, -1, -1);
        }
        catch (final Exception x)
        {
            log.info("problem using undocumented chmod api", x);
        }
    }
}
