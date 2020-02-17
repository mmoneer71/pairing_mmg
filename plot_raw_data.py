import pandas as pd
import matplotlib.pyplot as plt
import numpy as np
from scipy.fftpack import fftfreq, fft, ifft
import scipy.signal as sig
import smoothed_zscore as sz


def calculate_derivative_list(timestampList, toCalcList):
    if (timestampList is None or len(timestampList) == 0 or toCalcList is None 
        or len(toCalcList) == 0):
        ValueError(" calculate_derivative_list:  invalid parameters ")
    toCalc = np.asarray(toCalcList)
    resultList = []    
    resultList.append(0)
    for i in range(len(toCalc)):
        if i == len(toCalc)-1: 
            break
        resultList.append((toCalc[i + 1] - toCalc[i]) / (timestampList[i + 1] - timestampList[i]))
    return resultList

def apply_lowpass_filter(a, cutoff_freq):
    if (a is None or len(a) == 0 or cutoff_freq is None 
        or cutoff_freq<0):
        ValueError(" apply_lowpass_filter:  invalid parameters ")
    a[cutoff_freq:] = 0
    return a

# dictionary for the Grey-code extraction
grey_code_dict =	{
  '1': '01',
  '0': '00',
  '-1': '11'
}

# jump in terms of datapoint used for extracting the grey codedata_watch['x_acc_fin'][firstpeak_index:len(x_dy)]
jump = 2
# global variables used as parameters of Savitzky-Golay filers
window_length_savgol = 15
polyorder_savgol = 5
fc = 40


def grey_code_extraction_2bit(a, b):
    if (a is None or len(a) == 0 or b is None or len(b) == 0):
        ValueError(" grey_code_extraction:  invalid parameters ")
    i = 0
    bits_str = np.array([], dtype = str)
    while(i + jump < len(a) or i + jump < len(b)):        
        if (a[i + jump] - a[i] >= 0):
            app_str = '0'
            if (b[i + jump]- b[i] >= 0):
                app_str += '0'
            else:
                app_str += '1'
        else:
            app_str = '1'
            if (b[i + jump] - b[i] >= 0):
                app_str += '1'
            else:
                app_str += '0'
        bits_str = np.append(bits_str, app_str)
        i += 1
    return bits_str

def find_first_peak(peaks_array, orginal_array, window):
    if (peaks_array is None or len(peaks_array) == 0 or orginal_array is None 
        or len(orginal_array) == 0 or window <= 0):
        ValueError(" find_first_peak:  invalid parameters ")
    i = 0
    result = 0
    index = 0
    max_value = 0
    while(i < len(peaks_array) and index < window):        
        if(peaks_array[i] == 1):
            if (orginal_array[i] > max_value):
                max_value = orginal_array[i]
                result = i
            if (peaks_array[i - 1] != 1): # new peak ecounteredyour
                index += 1           
        i += 1
    return result

vel_file_path = 'Test_Data/Real_Beginning/logical_inch_conversion/Drawing_Data/2020-02-04_3_smartphone_sample.csv'
acc_file_path = 'Test_Data/Real_Beginning/logical_inch_conversion/Accelerometer_Data/2020-02-04_3_watch_sample.csv'


# DataFrame collection from files
data_phone = pd.read_csv(vel_file_path, engine='python')
data_watch = pd.read_csv(acc_file_path, engine='python')


#dario_dy = calculate_derivative_list(data_phone['timestamp'], data_phone['x_velocity'])

#dx = 0.02
#dy = np.diff(data_phone['x'])/dx

#print(len(data_phone), len(data_watch), len(dy), len(dario_dy))
#plt.plot(range(0, len(dy)), dy)
#plt.figure()
#plt.plot(range(0, len(dario_dy)), dario_dy)
#plt.figure()
#plt.plot(range(0, len(data_phone['x_velocity'])), data_phone['x_velocity'])
#plt.figure()
#plt.plot(range(0, len(data_watch['x_acc'])), data_watch['x_acc'])


#data_phone['filtered_x_vel'] = sig.savgol_filter(data_phone['x_velocity'], window_length_savgol, polyorder_savgol)
#data_phone['filtered_y_vel'] = sig.savgol_filter(data_phone['y_velocity'], window_length_savgol, polyorder_savgol)

x_dy = calculate_derivative_list(data_phone['timestamp'], data_phone['x_velocity'])

y_dy = calculate_derivative_list(data_phone['timestamp'], data_phone['y_velocity'])

x_dy_fft = fft(x_dy)
x_dy_fft = apply_lowpass_filter(x_dy_fft,fc)
x_dy = np.real(ifft(x_dy_fft))
x_dy = sig.savgol_filter(x_dy, window_length_savgol, polyorder_savgol)

y_dy_fft = fft(y_dy)
y_dy_fft = apply_lowpass_filter(y_dy_fft,fc)
y_dy = np.real(ifft(y_dy_fft))
y_dy = sig.savgol_filter(y_dy, window_length_savgol, polyorder_savgol)

# data_phone['phone_x_vel_fft'] = fft(np.array(data_phone['filtered_x_vel']))
# data_phone['phone_y_vel_fft'] = fft(np.array(data_phone['filtered_y_vel']))
# # rudimentary low-pass filter on the FFT of the velocity
# # https://dsp.stackexchange.com/questions/49460/apply-low-pass-butterworth-filter-in-python

# #print(len(data_phone['phone_x_vel_fft']))
# #w = fc / (fs / 2) # Normalize the frequency
# data_phone['phone_x_vel_fft_lp'] = apply_lowpass_filter(data_phone['phone_x_vel_fft'],fc)
# data_phone['phone_y_vel_fft_lp'] = apply_lowpass_filter(data_phone['phone_y_vel_fft'],fc)
# # Inverse FFT (lp stands for low-passed)
# data_phone['phone_x_vel_lp'] = ifft(data_phone['phone_x_vel_fft_lp'])
# data_phone['phone_y_vel_lp'] = ifft(data_phone['phone_y_vel_fft_lp'])

# data_phone['filtered_x_vel'] = sig.savgol_filter(data_phone['phone_x_vel_lp'], window_length_savgol, polyorder_savgol)
# data_phone['filtered_y_vel'] = sig.savgol_filter(data_phone['phone_y_vel_lp'], window_length_savgol, polyorder_savgol)

#data_watch['x_acc'] = data_watch['x_acc'] - data_watch['x_acc'].mean()
#data_watch['y_acc'] = data_watch['y_acc'] - data_watch['y_acc'].mean()
#data_watch['z_acc'] = data_watch['z_acc'] - data_watch['z_acc'].mean()


#data_watch['x_acc'] = sig.savgol_filter(data_watch['x_acc'], window_length_savgol, polyorder_savgol)
#data_watch['y_acc'] = sig.savgol_filter(data_watch['y_acc'], window_length_savgol, polyorder_savgol)
#data_watch['z_acc'] = sig.savgol_filter(data_watch['z_acc'], window_length_savgol, polyorder_savgol)

# calculate the Euclidean norm = 2-norm (that is: magnitude)
a = np.column_stack((data_watch['x_acc'], data_watch['y_acc'], data_watch['z_acc']))
b = np.zeros(a.shape[0])
for j in range(a.shape[0]):
    b[j] = np.linalg.norm(a[j]) # Euclidean norm = 2-norm 
data_watch['magnitude'] = b


#data_watch['magnitude'] = sig.savgol_filter(data_watch['magnitude'], window_length_savgol, polyorder_savgol)

smoothed_zscore = sz.thresholding_algo(data_watch['magnitude'], 4, 9, 0) # thresholding_algo(y, lag, threshold, influence)
data_watch['filtered_magnitude'] = smoothed_zscore.get('signals')

firstpeak_index = find_first_peak(np.array(data_watch['filtered_magnitude']), np.array(data_watch['magnitude']),5) # find out the beginning of the very first peak
print(firstpeak_index)

#data_watch['x_acc'] = data_watch['x_acc'] - data_watch['x_acc'][:len(x_dy)].mean()
#data_watch['y_acc'] = data_watch['y_acc'] - data_watch['y_acc'][:len(y_dy)].mean()

#plt.plot(data_watch['timestamp'], data_watch['z_acc'])
#for i in range(0, len(data_watch['z_acc']) - 1):
#    print(i, data_watch['z_acc'][i]) #16



data_watch['x_acc_fft'] = fft(np.array(data_watch['x_acc']))
data_watch['x_acc_fft_lp'] = apply_lowpass_filter(data_watch['x_acc_fft'],fc)
data_watch['x_acc_lp'] = np.real(ifft(data_watch['x_acc_fft_lp']))
data_watch['x_acc_fin'] = sig.savgol_filter(data_watch['x_acc_lp'], window_length_savgol, polyorder_savgol)


data_watch['y_acc_fft'] = fft(np.array(data_watch['y_acc']))
data_watch['y_acc_fft_lp'] = apply_lowpass_filter(data_watch['y_acc_fft'],fc)
data_watch['y_acc_lp'] = np.real(ifft(data_watch['y_acc_fft_lp']))
data_watch['y_acc_fin'] = sig.savgol_filter(data_watch['y_acc_lp'], window_length_savgol, polyorder_savgol)


watch_linear_x_acc_lp_resampled = sig.resample(data_watch['x_acc_fin'][firstpeak_index:], len(x_dy))
watch_linear_y_acc_lp_resampled = sig.resample(data_watch['y_acc_fin'][firstpeak_index:], len(y_dy))


plt.plot(data_phone['timestamp'], watch_linear_y_acc_lp_resampled)
plt.figure()
plt.plot(data_phone['timestamp'], y_dy)


watch_acc_greycode_2bit = grey_code_extraction_2bit(watch_linear_x_acc_lp_resampled, watch_linear_y_acc_lp_resampled)
phone_acc_greycode_2bit = grey_code_extraction_2bit(x_dy, y_dy)


match = 0

for i in range(0, len(phone_acc_greycode_2bit)):
    print(watch_acc_greycode_2bit[i], phone_acc_greycode_2bit[i])
    if watch_acc_greycode_2bit[i] == phone_acc_greycode_2bit[i]:
        match += 1

print(match / len(watch_acc_greycode_2bit))

plt.show()

#zeros_c = 0
#ones_c = 0
#longest_streak = 0
#streak = 0
#streak_loc = 0
#for i in range(0, len(watch_acc_greycode_2bit)):
    #print(watch_acc_greycode_2bit[i], phone_acc_greycode_2bit[i])
#    if watch_acc_greycode_2bit[i] == phone_acc_greycode_2bit[i]:
#        zeros_c += 1
#        streak += 1
#    else:
#        if streak > longest_streak:
#            longest_streak = streak
#            streak_loc = i
#        ones_c += 1
#        streak = 0
#print(zeros_c, ones_c + zeros_c, zeros_c / (ones_c + zeros_c) * 100) #, longest_streak, streak_loc)