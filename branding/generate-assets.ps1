# Generates derived brand assets from the source logo (branding/source/MedirianClient.png).
#
# Outputs (branding/):
#   medirian-mark.png          cropped mark, original colours (for light backgrounds)
#   medirian-mark-on-dark.png  cropped mark recoloured for dark UI (moon lavender + bright violet)
#   medirian-icon-512.png      app icon (dark rounded tile + on-dark mark)
#   medirian-icon-256.png
#   medirian-icon-128.png      Minecraft mod icon size
#
# and copies the in-game assets into the shared client resources:
#   client/shared/src/main/resources/assets/medirian/icon.png              (mod icon, 128 px)
#   client/shared/src/main/resources/assets/medirian/textures/gui/mark.png (menu logo, 96 px wide)
#
# Usage: powershell -ExecutionPolicy Bypass -File branding/generate-assets.ps1

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing
Add-Type -ReferencedAssemblies System.Drawing -TypeDefinition @'
using System;
using System.Drawing;
using System.Drawing.Imaging;
using System.Runtime.InteropServices;

public static class MedirianBrand {
    // Brand colours sampled from the source logo.
    static readonly int[] Plum   = { 0x2B, 0x0E, 0x3A };
    static readonly int[] Purple = { 0x5C, 0x1D, 0x7C };

    public static Rectangle OpaqueBounds(Bitmap src) {
        int minX = src.Width, minY = src.Height, maxX = -1, maxY = -1;
        for (int y = 0; y < src.Height; y++)
            for (int x = 0; x < src.Width; x++)
                if (src.GetPixel(x, y).A > 8) {
                    if (x < minX) minX = x; if (x > maxX) maxX = x;
                    if (y < minY) minY = y; if (y > maxY) maxY = y;
                }
        return Rectangle.FromLTRB(minX, minY, maxX + 1, maxY + 1);
    }

    /** Maps every pixel onto the plum→purple axis and re-projects it onto a new colour pair. */
    public static Bitmap Recolor(Bitmap src, Color newPlum, Color newPurple) {
        Bitmap dst = new Bitmap(src.Width, src.Height, PixelFormat.Format32bppArgb);
        for (int y = 0; y < src.Height; y++)
            for (int x = 0; x < src.Width; x++) {
                Color c = src.GetPixel(x, y);
                if (c.A == 0) { dst.SetPixel(x, y, Color.Transparent); continue; }
                double t = (c.R - Plum[0]) / (double)(Purple[0] - Plum[0]);
                t = Math.Max(0, Math.Min(1, t));
                int r = (int)Math.Round(newPlum.R + (newPurple.R - newPlum.R) * t);
                int g = (int)Math.Round(newPlum.G + (newPurple.G - newPlum.G) * t);
                int b = (int)Math.Round(newPlum.B + (newPurple.B - newPlum.B) * t);
                dst.SetPixel(x, y, Color.FromArgb(c.A, r, g, b));
            }
        return dst;
    }
}
'@

$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$source = Join-Path $root 'source\MedirianClient.png'
if (-not (Test-Path $source)) { throw "Missing source logo: $source" }

$src = [System.Drawing.Bitmap]::FromFile($source)
$bounds = [MedirianBrand]::OpaqueBounds($src)
$pad = 8
$crop = New-Object System.Drawing.Rectangle ($bounds.X - $pad), ($bounds.Y - $pad), ($bounds.Width + 2 * $pad), ($bounds.Height + 2 * $pad)
$mark = $src.Clone($crop, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
$src.Dispose()
$mark.Save((Join-Path $root 'medirian-mark.png'), [System.Drawing.Imaging.ImageFormat]::Png)

$onDark = [MedirianBrand]::Recolor($mark, [System.Drawing.Color]::FromArgb(0xE9, 0xE2, 0xF5), [System.Drawing.Color]::FromArgb(0x9B, 0x55, 0xD6))
$onDark.Save((Join-Path $root 'medirian-mark-on-dark.png'), [System.Drawing.Imaging.ImageFormat]::Png)

function New-Icon([int]$size, [string]$out) {
    $bmp = New-Object System.Drawing.Bitmap $size, $size, ([System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $g = [System.Drawing.Graphics]::FromImage($bmp)
    $g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
    $g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
    $g.Clear([System.Drawing.Color]::Transparent)

    # Rounded tile with a subtle violet glow from the top (moonlight), no loud gradients.
    $r = [int]($size * 0.22)
    $path = New-Object System.Drawing.Drawing2D.GraphicsPath
    $path.AddArc(0, 0, $r, $r, 180, 90); $path.AddArc($size - $r - 1, 0, $r, $r, 270, 90)
    $path.AddArc($size - $r - 1, $size - $r - 1, $r, $r, 0, 90); $path.AddArc(0, $size - $r - 1, $r, $r, 90, 90)
    $path.CloseFigure()
    $bg = New-Object System.Drawing.Drawing2D.LinearGradientBrush (New-Object System.Drawing.Point 0, 0), (New-Object System.Drawing.Point 0, $size), ([System.Drawing.Color]::FromArgb(255, 0x1C, 0x13, 0x2A)), ([System.Drawing.Color]::FromArgb(255, 0x0D, 0x0A, 0x14))
    $g.FillPath($bg, $path)
    $pen = New-Object System.Drawing.Pen ([System.Drawing.Color]::FromArgb(70, 0x9B, 0x55, 0xD6)), ([Math]::Max(1, $size / 128))
    $g.DrawPath($pen, $path)

    $w = [int]($size * 0.74); $h = [int]($w * $onDark.Height / $onDark.Width)
    $g.DrawImage($onDark, [int](($size - $w) / 2), [int](($size - $h) / 2 + $size * 0.01), $w, $h)
    $g.Dispose()
    $bmp.Save($out, [System.Drawing.Imaging.ImageFormat]::Png)
    $bmp.Dispose()
}

New-Icon 512 (Join-Path $root 'medirian-icon-512.png')
New-Icon 256 (Join-Path $root 'medirian-icon-256.png')
New-Icon 128 (Join-Path $root 'medirian-icon-128.png')

# In-game assets (shared by every Minecraft version adapter)
$assets = Join-Path $root '../client/shared/src/main/resources/assets/medirian'
New-Item -ItemType Directory -Force (Join-Path $assets 'textures/gui') | Out-Null
Copy-Item (Join-Path $root 'medirian-icon-128.png') (Join-Path $assets 'icon.png') -Force
$guiW = 96; $guiH = [int]($guiW * $onDark.Height / $onDark.Width)
$gui = New-Object System.Drawing.Bitmap $guiW, $guiH, ([System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
$gg = [System.Drawing.Graphics]::FromImage($gui)
$gg.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
$gg.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
$gg.DrawImage($onDark, 0, 0, $guiW, $guiH)
$gg.Dispose()
$gui.Save((Join-Path $assets 'textures/gui/mark.png'), [System.Drawing.Imaging.ImageFormat]::Png)
$gui.Dispose()

$mark.Dispose(); $onDark.Dispose()
Write-Host "Brand assets generated in $root"
