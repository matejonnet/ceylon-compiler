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
class Bug111() {
    void m1() {
        Integer nat = 0;
        Integer neg = nat.negativeValue;
    }
    void m2() {
        Integer|Integer num = 0;
        Integer mag = num.negativeValue;
    }
    void m3() {
        Integer[]|Integer[] nums = [1,2,3];
        if(nonempty nums){
            Integer|Integer|Null first = nums.first;
        }
    }

}