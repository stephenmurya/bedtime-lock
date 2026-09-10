import 'package:flutter/material.dart';
import 'package:forui/forui.dart';

abstract final class AppPalette {
  static const background = Color(0xFF11110F);
  static const surface = Color(0xFF1A1916);
  static const surfaceRaised = Color(0xFF22211D);
  static const foreground = Color(0xFFF2EFE8);
  static const muted = Color(0xFFA5A096);
  static const accent = Color(0xFFD5A86E);
  static const accentSoft = Color(0xFF3A2E20);
  static const success = Color(0xFF9DB8A5);
  static const error = Color(0xFFD38D82);
  static const border = Color(0x20F2EFE8);
}

FThemeData bedtimeTheme() {
  final base = FTheme.neutral.dark.touch;
  final colors = base.colors.copyWith(
    background: AppPalette.background,
    foreground: AppPalette.foreground,
    primary: AppPalette.accent,
    primaryForeground: AppPalette.background,
    secondary: AppPalette.surfaceRaised,
    secondaryForeground: AppPalette.foreground,
    muted: AppPalette.surface,
    mutedForeground: AppPalette.muted,
    card: AppPalette.surface,
    border: AppPalette.border,
    destructive: AppPalette.error,
    destructiveForeground: AppPalette.background,
    error: AppPalette.error,
    errorForeground: AppPalette.background,
  );

  return FThemeData(
    colors: colors,
    touch: true,
    typography: base.typography,
    style: base.style,
  );
}
