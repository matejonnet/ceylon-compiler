/*
 * Copyright Red Hat Inc. and/or its affiliates and other contributors
 * as indicated by the authors tag. All rights reserved.
 *
 * This copyrighted material is made available to anyone wishing to use,
 * modify, copy, or redistribute it subject to the terms and conditions
 * of the GNU General Public License version 2.
 * 
 * This particular file is subject to the "Classpath" exception as provided in the 
 * LICENSE file that accompanied this code.
 * 
 * This program is distributed in the hope that it will be useful, but WITHOUT A
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE.  See the GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License,
 * along with this distribution; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA  02110-1301, USA.
 */
@nomodel
class MethodWhileConditionList() {
    void m1(Integer x) {
        variable value a = 0;
        while (x > 0, x < 10) {
            a += x;
        } 
    }
    void m2(Anything x, Integer z) {
        variable value a = 0;
        while (is Integer y = x, y > 0, z < 10) {
            a += y;
        } 
    }
    
    void m3(Anything x, Integer z) {
        variable value a = 0;
        while (z < 10, is Integer y = x, y > 0) {
            a += y;
        } 
    }
    
    void m4(Anything x, Integer z) {
        variable value a = 0;
        while (z < 10, z > 0, is Integer y = x) {
            a += y;
        } 
    }
    
    void m5(Anything[] x) {
        variable value a = 0;
        while (nonempty x, is Integer y = x[0], y > 0) {
            a += y;
        } 
    }
    
    void m6(Anything[] x) {
        variable value a = 0;
        if (x[0] exists, is Integer y = x[0], y > 0) {
            a += y;
        } 
    }
}