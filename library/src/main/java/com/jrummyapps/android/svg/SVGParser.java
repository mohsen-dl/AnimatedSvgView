/*
 * Copyright (C) 2016 Jared Rummler <jared.rummler@gmail.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.jrummyapps.android.svg;

import android.graphics.Matrix;
import android.graphics.Path;
import android.graphics.RectF;

public class SVGParser {

  private static final RectF ARC_RECTF = new RectF();
  private static final Matrix ARC_MATRIX = new Matrix();
  private static final Matrix ARC_MATRIX_2 = new Matrix();

  private static float angle(float x1, float y1, float x2, float y2) {
    return (float) Math.toDegrees(Math.atan2(x1, y1) - Math.atan2(x2, y2)) % 360;
  }

  /**
   * Parses a single SVG path and returns it as a <code>android.graphics.Path</code> object. An example path is
   * <code>M250,150L150,350L350,350Z</code>, which draws a triangle.
   *
   * This is where the hard-to-parse paths are handled. Uppercase rules are absolute positions, lowercase are
   * relative. Types of path rules:
   * <p/>
   * <ol>
   * <li>M/m - (x y)+ - Move to (without drawing)
   * <li>Z/z - (no params) - Close path (back to starting point)
   * <li>L/l - (x y)+ - Line to
   * <li>H/h - x+ - Horizontal ine to
   * <li>V/v - y+ - Vertical line to
   * <li>C/c - (x1 y1 x2 y2 x y)+ - Cubic bezier to
   * <li>S/s - (x2 y2 x y)+ - Smooth cubic bezier to (shorthand that assumes the x2, y2 from previous C/S is the x1,
   * y1 of this bezier)
   * <li>Q/q - (x1 y1 x y)+ - Quadratic bezier to
   * <li>T/t - (x y)+ - Smooth quadratic bezier to (assumes previous control point is "reflection" of last one w.r.t.
   * to current point)
   * </ol>
   * <p/>
   * Numbers are separate by whitespace, comma or nothing at all (!) if they are self-delimiting, (ie. begin with a -
   * sign)
   *
   * @param s
   *     the SVG path, see the specification <a href="http://www.w3.org/TR/SVG/paths.html">here</a>.
   */
  public static Path parsePath(String s) {
    int n = s.length();
    ParserHelper ph = new ParserHelper(s, 0);
    ph.skipWhitespace();
    Path p = new Path();
    float lastX = 0;
    float lastY = 0;
    float lastX1 = 0;
    float lastY1 = 0;
    float subPathStartX = 0;
    float subPathStartY = 0;
    char prevCmd = 0;
    while (ph.pos < n) {
      char cmd = s.charAt(ph.pos);
      switch (cmd) {
        case '-':
        case '+':
        case '0':
        case '1':
        case '2':
        case '3':
        case '4':
        case '5':
        case '6':
        case '7':
        case '8':
        case '9':
          if (prevCmd == 'm' || prevCmd == 'M') {
            cmd = (char) ((prevCmd) - 1);
            break;
          } else if (("lhvcsqta").indexOf(Character.toLowerCase(prevCmd)) >= 0) {
            cmd = prevCmd;
            break;
          }
        default: {
          ph.advance();
          prevCmd = cmd;
        }
      }

      boolean wasCurve = false;
      switch (cmd) {
        case 'M':
        case 'm': {
          float x = ph.nextFloat();
          float y = ph.nextFloat();
          if (cmd == 'm') {
            subPathStartX += x;
            subPathStartY += y;
            p.rMoveTo(x, y);
            lastX += x;
            lastY += y;
          } else {
            subPathStartX = x;
            subPathStartY = y;
            p.moveTo(x, y);
            lastX = x;
            lastY = y;
          }
          break;
        }
        case 'Z':
        case 'z': {
          p.close();
          p.moveTo(subPathStartX, subPathStartY);
          lastX = subPathStartX;
          lastY = subPathStartY;
          lastX1 = subPathStartX;
          lastY1 = subPathStartY;
          wasCurve = true;
          break;
        }
        case 'T':
        case 't':
          // todo - smooth quadratic Bezier (two parameters)
        case 'L':
        case 'l': {
          float x = ph.nextFloat();
          float y = ph.nextFloat();
          if (cmd == 'l') {
            p.rLineTo(x, y);
            lastX += x;
            lastY += y;
          } else {
            p.lineTo(x, y);
            lastX = x;
            lastY = y;
          }
          break;
        }
        case 'H':
        case 'h': {
          float x = ph.nextFloat();
          if (cmd == 'h') {
            p.rLineTo(x, 0);
            lastX += x;
          } else {
            p.lineTo(x, lastY);
            lastX = x;
          }
          break;
        }
        case 'V':
        case 'v': {
          float y = ph.nextFloat();
          if (cmd == 'v') {
            p.rLineTo(0, y);
            lastY += y;
          } else {
            p.lineTo(lastX, y);
            lastY = y;
          }
          break;
        }
        case 'C':
        case 'c': {
          wasCurve = true;
          float x1 = ph.nextFloat();
          float y1 = ph.nextFloat();
          float x2 = ph.nextFloat();
          float y2 = ph.nextFloat();
          float x = ph.nextFloat();
          float y = ph.nextFloat();
          if (cmd == 'c') {
            x1 += lastX;
            x2 += lastX;
            x += lastX;
            y1 += lastY;
            y2 += lastY;
            y += lastY;
          }
          p.cubicTo(x1, y1, x2, y2, x, y);
          lastX1 = x2;
          lastY1 = y2;
          lastX = x;
          lastY = y;
          break;
        }
        case 'Q':
        case 'q':
          // todo - quadratic Bezier (four parameters)
        case 'S':
        case 's': {
          wasCurve = true;
          float x2 = ph.nextFloat();
          float y2 = ph.nextFloat();
          float x = ph.nextFloat();
          float y = ph.nextFloat();
          if (Character.isLowerCase(cmd)) {
            x2 += lastX;
            x += lastX;
            y2 += lastY;
            y += lastY;
          }
          float x1 = 2 * lastX - lastX1;
          float y1 = 2 * lastY - lastY1;
          p.cubicTo(x1, y1, x2, y2, x, y);
          lastX1 = x2;
          lastY1 = y2;
          lastX = x;
          lastY = y;
          break;
        }
        case 'A':
        case 'a': {
          float rx = ph.nextFloat();
          float ry = ph.nextFloat();
          float theta = ph.nextFloat();
          int largeArc = ph.nextFlag();
          int sweepArc = ph.nextFlag();
          float x = ph.nextFloat();
          float y = ph.nextFloat();
          if (cmd == 'a') {
            x += lastX;
            y += lastY;
          }
          drawArc(p, lastX, lastY, x, y, rx, ry, theta, largeArc, sweepArc);
          lastX = x;
          lastY = y;
          break;
        }
        default:
          ph.advance();
      }
      if (!wasCurve) {
        lastX1 = lastX;
        lastY1 = lastY;
      }
      ph.skipWhitespace();
    }
    return p;
  }

  private static void drawArc(Path p, float lastX, float lastY, float x, float y, float rx, float ry, float theta,
                              int largeArc, int sweepArc) {
    // http://www.w3.org/TR/SVG/implnote.html#ArcImplementationNotes

    if (rx == 0 || ry == 0) {
      p.lineTo(x, y);
      return;
    }

    if (x == lastX && y == lastY) {
      return; // nothing to draw
    }

    rx = Math.abs(rx);
    ry = Math.abs(ry);

    final float thrad = theta * (float) Math.PI / 180;
    final float st = (float) Math.sin(thrad);
    final float ct = (float) Math.cos(thrad);

    final float xc = (lastX - x) / 2;
    final float yc = (lastY - y) / 2;
    final float x1t = ct * xc + st * yc;
    final float y1t = -st * xc + ct * yc;

    final float x1ts = x1t * x1t;
    final float y1ts = y1t * y1t;
    float rxs = rx * rx;
    float rys = ry * ry;

    float lambda = (x1ts / rxs + y1ts / rys) * 1.001f; // add 0.1% to be sure that no out of range occurs due to
    // limited precision
    if (lambda > 1) {
      float lambdasr = (float) Math.sqrt(lambda);
      rx *= lambdasr;
      ry *= lambdasr;
      rxs = rx * rx;
      rys = ry * ry;
    }

    final float R = (float) (Math.sqrt((rxs * rys - rxs * y1ts - rys * x1ts) / (rxs * y1ts + rys * x1ts)) *
        ((largeArc == sweepArc) ? -1 : 1));
    final float cxt = R * rx * y1t / ry;
    final float cyt = -R * ry * x1t / rx;
    final float cx = ct * cxt - st * cyt + (lastX + x) / 2;
    final float cy = st * cxt + ct * cyt + (lastY + y) / 2;

    final float th1 = angle(1, 0, (x1t - cxt) / rx, (y1t - cyt) / ry);
    float dth = angle((x1t - cxt) / rx, (y1t - cyt) / ry, (-x1t - cxt) / rx, (-y1t - cyt) / ry);

    if (sweepArc == 0 && dth > 0) {
      dth -= 360;
    } else if (sweepArc != 0 && dth < 0) {
      dth += 360;
    }

    // draw
    if ((theta % 360) == 0) {
      // no rotate and translate need
      ARC_RECTF.set(cx - rx, cy - ry, cx + rx, cy + ry);
      p.arcTo(ARC_RECTF, th1, dth);
    } else {
      // this is the hard and slow part :-)
      ARC_RECTF.set(-rx, -ry, rx, ry);

      ARC_MATRIX.reset();
      ARC_MATRIX.postRotate(theta);
      ARC_MATRIX.postTranslate(cx, cy);
      ARC_MATRIX.invert(ARC_MATRIX_2);

      p.transform(ARC_MATRIX_2);
      p.arcTo(ARC_RECTF, th1, dth);
      p.transform(ARC_MATRIX);
    }
  }

}
