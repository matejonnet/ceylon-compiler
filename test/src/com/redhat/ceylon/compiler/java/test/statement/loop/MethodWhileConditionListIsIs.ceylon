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
class MethodWhileConditionListIsIs() {
    Boolean m(Anything x, Anything y) {
        while (is Integer x1=x, is Integer y1=y) {
            return x1 == y1;
        }
        return false;
    }
    Boolean synthetic(Anything x, Anything y) {
        while (is Integer x, is Integer y) {
            return x == y;
        }
        return false;
    }
    Boolean mElseIf(Anything x, Anything y) {
        while (is Integer x1=x, is Integer y1=y) {
            return x1 == y1;
        }
        return false;
    }
}